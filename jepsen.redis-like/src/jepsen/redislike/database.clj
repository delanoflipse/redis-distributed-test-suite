(ns jepsen.redislike.database
  (:require [clojure.tools.logging :refer [info warn]]
            [clojure.string :as str]
            [jepsen
             [control :as c]
             [core :as jepsen]
             [util :as util]
             [db :as db]]
            [jepsen.redislike.util :as p-util]
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]
            [slingshot.slingshot :refer [try+ throw+]]))


(def working-dir     "/opt/redis")
(def node-port     7000)
(def replicas-per-main     1)
(def conf-file     "redis.conf")
(def binary "redis-server")
(def db-file        "redis.rdb")
(def logfile (str working-dir "/db.log"))
(def pidfile (str working-dir "/db.pid"))

(def build-file
  "A file we create to track the last built version; speeds up compilation."
  "jepsen-built-version")

(def checkout-root-dir "/repos")
(def build-repository "https://github.com/redis/redis")
(def build-repository-name "redis")
(def build-repository-version "0a8a45f")

;; generated files
(def build-repository-path  (str checkout-root-dir "/" build-repository-name))
(def build-full-path  (str build-repository-path "/" build-repository-version))

(defn install-tools!
  "Installs prerequisite packages for building redis and redisraft."
  []
  (debian/install [:lsb-release :build-essential :cmake :libtool :autoconf :automake]))

(defn checkout-repo!
  "Clone a repository in directory ${checkout-root-dir}/${repo-name}/${version}"
  []
  (when-not (cu/exists? build-full-path)
    (c/exec :mkdir :-p  build-repository-path)
    (c/cd build-repository-path
          (info "Cloning into" build-full-path)
          (c/exec :git :clone build-repository build-repository-version))
    (c/cd build-full-path
          (info "Checking out latest version")
          (try+ (c/exec :git :checkout build-repository-version)
                (catch [:exit 1] e
                  (if (re-find #"pathspec .+ did not match any file" (:err e))
                    (do ; Ah, we're out of date
                      (c/exec :git :fetch)
                      (c/exec :git :checkout build-repository-version))
                    (throw+ e)))))))

(def build-locks
  "We use these locks to prevent concurrent builds."
  (util/named-locks))

(defmacro with-build-version
  [node repo-name version & body]
  `(util/with-named-lock build-locks [~node ~repo-name]
     (let [build-file# (str build-full-path "/" build-file)]
       (if (try+ (= (str ~version) (c/exec :cat build-file#))
                 (catch [:exit 1] e# ; Not found
                   false))
         ; Already built
         (str build-full-path)
         ; Build
         (let [res# (do ~@body)]
           ; Log version
           (c/exec :echo ~version :> build-file#)
           res#)))))

(defn build-db!
  "Compiles redis, and returns the directory we built in."
  [node]
  (with-build-version node build-repository-name build-repository-version
    (do
      (checkout-repo!)
      (info "Building redis" build-repository-version)
      (c/cd build-full-path
            (c/exec :make))
      )))

(defn deploy-binaries!
  "Uploads binaries built from the given directory."
  [build-dir executables]
  (c/exec :mkdir :-p working-dir)
  (doseq [f executables]
    (c/exec :cp (str build-dir "/src/" f) (str working-dir "/"))
    (c/exec :chmod "+x" (str working-dir "/" f))))

(defn db
  "Redis-like DB for a particular version."
  [database version setuptype]
  (reify db/DB
    (setup! [_ test node]
      (info node "installing DB" database version setuptype)
      ;; https://redis.io/docs/getting-started/installation/install-redis-from-source/
      (c/su
       (info "installing tools")
       (install-tools!)
       
       (c/exec :mkdir :-p working-dir)

       (info "installing database")
       (build-db! node)

       (info "setting up binaries as executables")
       (deploy-binaries! build-full-path  ["redis-server" "redis-cli"])

       (info "writing config")
       (c/cd working-dir
             (cu/write-file! "
port 7000
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
appendonly yes
                       " conf-file))

       (cu/start-daemon!
        {:logfile logfile
         :pidfile pidfile
         :chdir   working-dir}
        binary
        conf-file
        :--protected-mode         "no"
        :--bind                   "0.0.0.0"
        :--dbfilename             db-file
        :--loglevel                 "debug")
       (Thread/sleep 1000)
       (jepsen/synchronize test)
       (info "all servers started")

       (if (= node (jepsen/primary test))
          ; Initialize the cluster on the primary
         (let [nodes-urls (str/join " " (map p-util/node-url (:nodes test) (repeat node-port)))]
           (info "Creating primary cluster" nodes-urls)
           (c/cd working-dir
                 (c/exec (c/lit "./redis-cli") :--cluster :create (c/lit nodes-urls) :--cluster-yes :--cluster-replicas (c/lit (str replicas-per-main))))
           (info "Main init done, syncing...")
           (jepsen/synchronize test))
          ; And join on secondaries.
         (do
           (info "Waiting for primary to start cluster...")
           (jepsen/synchronize test)
           (info "Waiting for secondary to join...")
           (Thread/sleep 2000)))

       (Thread/sleep 2000)
       (info "Synced")))

    (teardown! [_ test node]
      (info node "tearing down DB")
      (cu/stop-daemon! binary pidfile)
      (c/su (c/exec :rm :-rf working-dir)))

    db/LogFiles
    (log-files [_ test node]
      [logfile])))
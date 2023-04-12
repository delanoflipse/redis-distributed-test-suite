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


;; running configuration
(def replicas-per-main 1)

;; server configuration

;; REDIS
(def conf-file "redis.conf")
(def db-binary "redis-server")
(def db-cli "redis-cli")
(def build-repository "https://github.com/redis/redis")
(def build-repository-name "redis")
(def build-repository-version "0a8a45f")

;; KEYDB
;; (def conf-file "keydb.conf")
;; (def db-binary "keydb-server")
;; (def db-cli "keydb-cli")
;; (def build-repository "https://github.com/Snapchat/KeyDB")
;; (def build-repository-name "keydb")
;; (def build-repository-version "478ed26")

;; generic configuration
(def working-dir "/opt/db")
(def db-file "db.rdb")
(def node-port 7000)
(def checkout-root-dir "/repos")
(def build-file
  "A file we create to track the last built version; speeds up compilation."
  "jepsen-built-version")

;; generated variables
(def build-repository-path  (str checkout-root-dir "/" build-repository-name))
(def build-full-path  (str build-repository-path "/" build-repository-version))
(def logfile (str working-dir "/db.log"))
(def pidfile (str working-dir "/db.pid"))

;; procedures
(defn install-tools!
  "Installs prerequisite packages for building redis and redisraft."
  []
  (debian/install [:lsb-release :build-essential :cmake :libtool :autoconf :automake :nasm :autotools-dev :autoconf :libjemalloc-dev :tcl :tcl-dev :uuid-dev :libcurl4-openssl-dev :libbz2-dev :libzstd-dev :liblz4-dev :libsnappy-dev :libssl-dev :pkg-config]))

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
                      (c/exec :git :checkout build-repository-version)
                      (c/exec :git :submodule :init)
                      (c/exec :git :submodule :update)
                      
                      )
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
  "Compiles db, and returns the directory we built in."
  [node]
  (with-build-version node build-repository-name build-repository-version
    (do
      (checkout-repo!)
      (info "Building version" build-repository-version)
      (c/cd build-full-path
      ;; https://redis.io/docs/getting-started/installation/install-redis-from-source/
            (c/exec :make)))))

(defn deploy-binaries!
  "Uploads binaries built from the given directory."
  [build-dir executables]
  (c/exec :mkdir :-p working-dir)
  (doseq [f executables]
    (c/exec :cp (str build-dir "/src/" f) (str working-dir "/"))
    (c/exec :chmod "+x" (str working-dir "/" f))))


;; Database definition
(defn db
  "Redis-like DB for a particular version."
  [database version setuptype]
  (reify db/DB
    (setup! [_ test node]
      (info node "installing DB" database version setuptype)
      (c/su
       (info "installing tools")
       (install-tools!)

       (c/exec :mkdir :-p working-dir)

       (info "installing database")
       (build-db! node)

       (info "setting up binaries as executables")
       (deploy-binaries! build-full-path  [db-binary db-cli])

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
        db-binary
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
                 (c/exec (c/lit (str "./" db-cli)) :--cluster :create (c/lit nodes-urls) :--cluster-yes :--cluster-replicas (c/lit (str replicas-per-main))))
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
      (cu/stop-daemon! db-binary pidfile)
      (c/su (c/exec :rm :-rf working-dir)))

    db/LogFiles
    (log-files [_ test node]
      [logfile])))
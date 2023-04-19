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

;; CONSTANTS
(def working-dir "/opt/db")
(def db-file "db.rdb")
(def checkout-root-dir "/repos")
(def build-file
  "A file we create to track the last built version; speeds up compilation."
  "jepsen-built-version")
(def logfile (str working-dir "/db.log"))
(def pidfile (str working-dir "/db.pid"))

;; Per-database options
(def db-defaults {:redis {:conf-file "redis.conf"
                          :db-binary "redis-server"
                          :db-cli "redis-cli"
                          :build-repository "https://github.com/redis/redis"
                          :build-repository-name "redis"
                          :build-repository-version "f651708"}
                  :keydb {:conf-file "keydb.conf"
                          :db-binary "keydb-server"
                          :db-cli "keydb-cli"
                          :build-repository "https://github.com/Snapchat/KeyDB"
                          :build-repository-name "keydb"
                          :build-repository-version "478ed26"}})


;; --- Database configuration utilities ---
(defn db-opts!
  "Inject inferred options, mostly based on database choice"
  [args]
  (let [defaults (get db-defaults (keyword (:database args)) (:redis db-defaults))
        build-repository-path (str checkout-root-dir "/" (:build-repository-name defaults))]
    (merge defaults {:build-repository-path  build-repository-path
                     :build-full-path  (str build-repository-path "/" (:build-repository-version defaults))})))

;; --- Database build utilities ---
;; Most of the installation tools were adopted from https://github.com/jepsen-io/redis/blob/54ab5c10ace229fc780e43e188ef1b64c3a32ee4/src/jepsen/redis/db.clj
;; Building 9x redis on every test run is a waste of time and energy.
(defn install-tools!
  "Installs prerequisite packages for building redis and keybdb."
  []
  (debian/install [:lsb-release :build-essential :cmake :libtool :autoconf :automake :nasm :autotools-dev :autoconf :libjemalloc-dev :tcl :tcl-dev :uuid-dev :libcurl4-openssl-dev :libbz2-dev :libzstd-dev :liblz4-dev :libsnappy-dev :libssl-dev :pkg-config :git]))

(defn checkout-repo!
  "Clone a repository in directory ${checkout-root-dir}/${repo-name}/${version}"
  [opts]
  (when-not (cu/exists? (:build-full-path opts))
    (c/exec :mkdir :-p  (:build-repository-path opts))
    (c/cd (:build-repository-path opts)
          (info "Cloning into" (:build-full-path opts))
          (c/exec :git :clone (:build-repository opts) (:build-repository-version opts)))
    (c/cd (:build-full-path opts)
          (info "Checking out latest version")
          (try+ (c/exec :git :checkout (:build-repository-version opts))
                (catch [:exit 1] e
                  (if (re-find #"pathspec .+ did not match any file" (:err e))
                    (do ; Ah, we're out of date
                      (c/exec :git :fetch)
                      (c/exec :git :checkout (:build-repository-version opts))
                      (c/exec :git :submodule :init)
                      (c/exec :git :submodule :update))
                    (throw+ e)))))))

(def build-locks
  "We use these locks to prevent concurrent builds."
  (util/named-locks))

(defmacro with-build-version
  "Only run the body, containing the build process, once."
  [node repo-name version repo-path & body]
  `(util/with-named-lock build-locks [~node ~repo-name]
     (let [build-file# (str ~repo-path "/" build-file)]
       (if (try+ (= (str ~version) (c/exec :cat build-file#))
                 (catch [:exit 1] e# ; Not found
                   false))
         ; Already built
         (str build-file#)
         ; Build
         (let [res# (do ~@body)]
           ; Log version
           (c/exec :echo ~version :> build-file#)
           res#)))))

(defn build-db!
  "Compiles the node's database of choice, as defined in the arguments."
  [node args]
  (let [repo-name (:build-repository-name args)
        repo-version (:build-repository-version args)
        repo-path (:build-full-path args)]
    (with-build-version node repo-name repo-version repo-path
      (do
        (checkout-repo! args)
        (info "Building version" repo-version)
        (c/cd repo-path
      ;; https://redis.io/docs/getting-started/installation/install-redis-from-source/
              (c/exec :make))))))

(defn deploy-binaries!
  "Moves built binaries from the given directory to the working directory."
  [build-dir executables]
  (c/exec :mkdir :-p working-dir)
  (doseq [f executables]
    (c/exec :cp (str build-dir "/src/" f) (str working-dir "/"))
    (c/exec :chmod "+x" (str working-dir "/" f))))

;; Database definition
(defn db
  "Redis-like DB for a particular version."
  []
  (reify db/DB
    ;; Build and setup a Redis Cluster. See https://redis.io/docs/management/scaling/ for more information
    (setup! [this test node]
      (info node "setting up DB...")
      (c/su
       (info "installing tools")
       (install-tools!)

       (c/exec :mkdir :-p working-dir)

       (info "installing database")
       (build-db! node test)

       (info "setting up binaries as executables")
       (deploy-binaries! (:build-full-path test)  [(:db-binary test) (:db-cli test)])

       (info "writing config")
       (c/cd working-dir
            ;;  This could be extended to test multiple configurations
             (cu/write-file! (str "
port " (:port test) "
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 1000
appendonly yes
# cluster-require-full-coverage no
# appendfsync always
                     ") (:conf-file test)))

       ;; We have now everything we need to start the database
       (db/start! this test node)
       (Thread/sleep 1000)
       (jepsen/synchronize test 10000)
       ;; Give every node the time to start up
       (info "all servers started")

       (if (= node (jepsen/primary test))
          ; Initialize the cluster on the primary
         (let [nodes-urls (str/join " " (map p-util/node-url (:nodes test) (repeat (:port test))))]
           (info "Creating primary cluster" nodes-urls)
           (c/cd working-dir
                 ;; Run te command to start the cluster.
                 ;; Not sure why this couldn't be included in the configuration file, many DB's seem to have this.
                 (c/exec (c/lit (str "./" (:db-cli test))) :--cluster :create (c/lit nodes-urls) :--cluster-yes :--cluster-replicas (:replicas test)))
           (info "Main init done, syncing...")
           (jepsen/synchronize test))
          ; And join on secondaries.
         (do
           (info "Waiting for primary to start cluster...")
           (jepsen/synchronize test)
           (info "Waiting for secondary to join...")
           (Thread/sleep 2000)))
       ;; Everyone is up and running!
       (Thread/sleep 2000)
       (info "Synced")))

    (teardown! [this test node]
      (info node "tearing down DB")
      (db/kill! this test node)
      (c/su (c/exec :rm :-rf working-dir)))

    db/Process
    (start! [_ test node]
      (c/su
       (info node :starting :redis)

       (cu/start-daemon!
        {:logfile logfile
         :pidfile pidfile
         :chdir   working-dir}
        (:db-binary test)
        (:conf-file test)
        :--protected-mode         "no"
        :--bind                   "0.0.0.0"
        :--dbfilename             db-file
        :--loglevel                 "debug")))

    (kill! [_ test _]
      (c/su
       (cu/stop-daemon! (:db-binary test) pidfile)))

    db/Pause
    (pause!  [_ test _] (c/su (cu/grepkill! :stop (:db-binary test))))
    (resume! [_ test _] (c/su (cu/grepkill! :cont (:db-binary test))))

    db/LogFiles
    (log-files [_ _ _] [logfile])))
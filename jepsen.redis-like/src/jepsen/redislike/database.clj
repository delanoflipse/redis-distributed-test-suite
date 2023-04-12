(ns jepsen.redislike.database
  (:require [clojure.tools.logging :refer [info warn]]
            [clojure.string :as str]
            [jepsen [cli :as cli]
             [control :as c]
             [core :as jepsen]
             [db :as db]
             [tests :as tests]]
            [jepsen.redislike.util :as p-util]
            [jepsen.control.util :as cu]
            [jepsen.control.net :as net]
            [jepsen.os.debian :as debian]))


(def dir     "/opt/redis")
(def node-port     7000)
(def conf-file     "redis.conf")
(def binary "redis-server")
(def db-file        "redis.rdb")
(def logfile (str dir "/db.log"))
(def pidfile (str dir "/db.pid"))

(def db-build-dir
  "The remote directory where we deploy redis to"
  (str dir "/build"))

;; TODO: make test specific
(def db-source-url
  "The source url to build the DB from"
  "https://download.redis.io/redis-stable.tar.gz")

(defn install-tools!
  "Installs prerequisite packages for building redis and redisraft."
  []
  (debian/install [:lsb-release :build-essential :cmake :libtool :autoconf :automake]))

(defn deploy-binaries!
  "Uploads binaries built from the given directory."
  [build-dir executables]
  (info "setting up binaries as executables")
  (c/exec :mkdir :-p dir)
  (doseq [f executables]
    (c/exec :cp (str build-dir "/src/" f) (str dir "/"))
    (c/exec :chmod "+x" (str dir "/" f))))

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
       (info "creating directory")
       (c/exec :mkdir :-p db-build-dir)
       (info "downloading archive")
       (cu/install-archive! db-source-url db-build-dir)
       (c/cd db-build-dir
             (info "building binaries")
             (c/exec :make))
       (deploy-binaries! db-build-dir  ["redis-server" "redis-cli"])

       (info "writing config")
       (c/cd dir
             (cu/write-file! "
port 7000
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
appendonly yes
                       " conf-file))
       (info (c/exec :cat :< (str dir "/" conf-file)))


       (cu/start-daemon!
        {:logfile logfile
         :pidfile pidfile
         :chdir   dir}
        binary
        conf-file
        :--protected-mode         "no"
        :--bind                   "0.0.0.0"
        :--dbfilename             db-file
        :--loglevel                 "debug")
       (Thread/sleep 2000)
       (if (= node (jepsen/primary test))
              ; Initialize the cluster on the primary
         (let [nodes-urls (str/join " " (map p-util/node-url (:nodes test) (repeat node-port)))]
           (info "Creating primary cluster" nodes-urls)
           (c/exec :redis-cli :--cluster :create (c/lit nodes-urls) :--cluster-yes))
              ; And join on secondaries.
         (info "Secondary setup?"))))

    (teardown! [_ test node]
      (info node "tearing down DB")
      (cu/stop-daemon! binary pidfile)
      (c/su (c/exec :rm :-rf dir)))

    db/LogFiles
    (log-files [_ test node]
      [logfile])))
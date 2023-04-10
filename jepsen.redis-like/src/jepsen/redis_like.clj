(ns jepsen.redis-like
  (:require [clojure.tools.logging :refer :all]
            [clojure.string :as str]
            [jepsen [cli :as cli]
             [control :as c]
             [db :as db]
             [tests :as tests]]
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]))

;; (def db-build-dir
;;   "A remote directory for us to clone projects and compile them."
;;   "/tmp/jepsen/build")


(def dir     "/opt/redis")
(def binary "etcd")
(def logfile (str dir "/etcd.log"))
(def pidfile (str dir "/etcd.pid"))

(def db-build-dir
  "The remote directory where we deploy redis to"
  dir)

(def db-source-url
  "The source url to build the DB from"
  "https://download.redis.io/redis-stable.tar.gz")

(defn install-tools!
  "Installs prerequisite packages for building redis and redisraft."
  []
  (c/su (debian/install [:lsb-release :build-essential :cmake :libtool :autoconf :automake])))

(defn db
  "Redis-like DB for a particular version."
  [database version setuptype]
  (reify db/DB
    (setup! [_ test node]
      (info node "installing DB" database version setuptype)
      ;; https://redis.io/docs/getting-started/installation/install-redis-from-source/
      (install-tools!)
      (cu/install-archive! db-source-url db-build-dir)
      (c/cd db-build-dir
            (c/exec :make)
            (c/exec :make :install)))

    (teardown! [_ test node]
      (info node "tearing down DB"))))

(defn redis-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:name "redis-like"
          :os   debian/os
          :db   (db "redis" "vx.y.z" "cluster")
          :pure-generators true}))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn redis-test})
                   (cli/serve-cmd))
            args))
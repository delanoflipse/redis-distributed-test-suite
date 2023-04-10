(ns jepsen.redislike.core
  (:require [clojure.tools.logging :refer [info warn]]
            [jepsen [cli :as cli]
             [tests :as tests]]
            [jepsen.redislike
             [database :as db-def]]
            [jepsen.os.debian :as debian]))


(defn redis-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:name "redislike"
          :os   debian/os
          :db   (db-def/db "redis" "vx.y.z" "cluster")
          :pure-generators true}))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn redis-test})
                   (cli/serve-cmd))
            args))
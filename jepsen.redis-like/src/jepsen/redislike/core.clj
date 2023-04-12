(ns jepsen.redislike.core
  (:require [clojure.tools.logging :refer [info warn]]
            [jepsen
             [cli :as cli]
             [generator :as gen]
             [tests :as tests]]
            [jepsen.redislike
             [client :as client]
             [database :as db-def]]
            [jepsen.os.debian :as debian]))

(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (str (rand-int 5))})

(defn redis-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:name "redislike"
          :os   debian/os
          :db   (db-def/db "redis" "vx.y.z" "cluster")
          :client (client/->RedisClient nil)
          :generator       (->> (gen/mix [r w])
                                (gen/stagger 1)
                                (gen/nemesis nil)
                                (gen/time-limit 15))
          :pure-generators true}))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn redis-test})
                   (cli/serve-cmd))
            args))
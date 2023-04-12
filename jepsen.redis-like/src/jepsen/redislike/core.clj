(ns jepsen.redislike.core
  (:require [clojure.tools.logging :refer [info warn]]
            [elle.list-append :as a]
            [jepsen
             [checker :as checker]
             [cli :as cli]
             [generator :as gen]
             [nemesis :as nemesis]
             [tests :as tests]]
            [jepsen.checker.timeline :as timeline]
            [jepsen.redislike
             [database :as db-def]
             [nemesis :as db-nemesis]
             [client :as client]]
            [jepsen.os.debian :as debian]
            [jepsen.redislike.nemesis :as redisnemesis]))

(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn append   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})

(defn elle-checker
  "wrapper around elle so jepsen can use it"
  []
  (reify checker/Checker
    (check [this test history opts]
      (a/check {:consistency-models [:serializable], :directory "out"} history))))

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
          :generator       (->> (gen/mix [r append])
                                (gen/stagger 1)
                                (gen/nemesis 
								  (redisnemesis/nemesisgenerator)
								)
                                (gen/time-limit 15))
          :pure-generators true
          :nemesis (redisnemesis/nemesisoptions)
          :checker (checker/compose {:perf        (checker/perf)
                                     :timeline    (timeline/html)
                                     :stats       (checker/stats)
                                     ;; :elle        (elle-checker)
									 })}))

;; TODO: automate node count
(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn redis-test})
                   (cli/serve-cmd))
            args))
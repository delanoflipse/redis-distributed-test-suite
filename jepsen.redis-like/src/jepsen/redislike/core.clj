(ns jepsen.redislike.core
  (:require [clojure.tools.logging :refer [info warn]]
            [jepsen
             [checker :as checker]
             [cli :as cli]
             [generator :as gen]
             [util :as util :refer [parse-long]]
             [nemesis :as nemesis]
             [tests :as tests]]
            [jepsen.checker.timeline :as timeline]
            [jepsen.redislike
             [database :as db-def]
             [nemesis :as db-nemesis]
             [client :as client]]
            [jepsen.os.debian :as debian]
            [jepsen.tests.cycle.append :as append]))


(defn as-node-host [num] (str "n" (+ num 1)))

(defn redis-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (let [db (db-def/db)
        workload (append/test
                  {; Exponentially distributed, so half of the time it's gonna
                    ; be one key, 3/4 of ops will use one of 2 keys, 7/8 one of
                    ; 3 keys, etc.
                   :key-count          (:key-count opts 12)
                   :min-txn-length     1
                   :max-txn-length     (:max-txn-length opts 1)
                   :max-writes-per-key (:max-writes-per-key opts 128)
                   :consistency-models [:strict-serializable]})
        nemesis (db-nemesis/nemesis opts db)
]

    (merge tests/noop-test
           opts

           {:name "redislike"
            :os   debian/os
            :db   db
            :nodes (into [] (map as-node-host (range (:node-count opts))))
            :client (client/->RedisClient nil)
            :generator       (->>
                              (:generator workload)
                              (gen/stagger (/ (:rate opts)))
                              (gen/nemesis (:generator nemesis))
                              (gen/time-limit  (:time-limit opts)))
            :pure-generators true
            :nemesis (:nemesis nemesis)
            :final-generator (:final-generator nemesis)
            :checker (checker/compose {:perf        (checker/perf {:perf (:perf nemesis)})
                                       :timeline    (timeline/html)
                                       :exceptions  (checker/unhandled-exceptions)
                                       :workload (:checker workload)
                                       :stats       (checker/stats)})})))

(def cli-opts
  "Options for test runners."
  [["-c" "--node-count INT" "Amount of nodes"
    :parse-fn parse-long
    :default 6]

   [nil "--client-timeout INT" "Timeout for jedis client in ms"
    :parse-fn parse-long
    :default 1000]

   [nil "--client-max-retries INT" "Client retries"
    :parse-fn parse-long
    :default 1]

   ["-p" "--port INT" "DB node port"
    :default 7000]

   [nil "--replicas INT" "Replicas per primary"
    :parse-fn parse-long
    :default 1]

   [nil "--max-txn-length INT" "What's the most operations we can execute per transaction?"
    :parse-fn parse-long
    :validate [pos? "must be positive"]]

   ["-r" "--rate HZ" "Approximate number of requests per second per thread"
    :default 10
    :parse-fn read-string
    :validate [#(and (number? %) (pos? %)) "must be a positive number"]]])

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn redis-test
                                         :opt-spec cli-opts})
                   (cli/serve-cmd))
            args))
(ns jepsen.redislike.core
  (:require [clojure.tools.logging :refer [info warn]]
            [elle.list-append :as a]
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

(defn redis-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (let [workload (append/test
                  {; Exponentially distributed, so half of the time it's gonna
                    ; be one key, 3/4 of ops will use one of 2 keys, 7/8 one of
                    ; 3 keys, etc.
                   :key-count          (:key-count opts 12)
                   :min-txn-length     1
                   :max-txn-length     (:max-txn-length opts 1)
                   :max-writes-per-key (:max-writes-per-key opts 128)
                   :consistency-models [:strict-serializable]})]

    (merge tests/noop-test
           opts

           {:name "redislike"
            :os   debian/os
            :db   (db-def/db "redis" "vx.y.z" "cluster")
            :client (client/->RedisClient nil)
            :generator       (->> ;;(gen/mix [r append])
                              (:generator workload)
                              (gen/stagger (/ (:rate opts)))
                              (gen/nemesis
                               (db-nemesis/nemesisgenerator))
                              (gen/time-limit  (:time-limit opts)))
            :pure-generators true
            :nemesis (db-nemesis/nemesisoptions)
            :checker (checker/compose {:perf        (checker/perf)
                                       :timeline    (timeline/html)
                                       :workload (:checker workload)
                                       :stats       (checker/stats)})})))

(def cli-opts
  "Options for test runners."
;; TODO: automate node count
  [["-c" "--node-count" "Amount of nodes"
    :default 6]

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
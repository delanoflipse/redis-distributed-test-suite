(ns jepsen.redislike.core
  (:require [clojure.tools.logging :refer [info warn]]
            [clojure [string :as str]]
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

;; Convert nth node to hostname. e.g. 1 -> n1
(defn as-node-host [num] (str "n" (+ num 1)))

;; Our test definition
(defn redis-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts#]
  (let [;; We inject some inferred arguments here, that way you can more easily switch databases.
        opts (merge opts# (db-def/db-opts! opts#))
        ;; Define our DB
        db (db-def/db)
        ;; for automatic fault detection, we expect :db in opts
        opts     (assoc opts :db db)
        ;; Elle append test auto client payload creation.
        ;; This was quite hidden, luckily Redis Raft also used it.
        workload (append/test
                  {:key-count          (:key-count opts) ;; If you know a way to make these keys actually evenly point to the same node (due to sharding), please let us know.
                   :min-txn-length     1 ;; Writing nothing is useless.
                   :max-txn-length     (:max-txn-length opts 1) ;; You can do more than one, but Redis Cluster has no transactions so yeah...
                   :max-writes-per-key (:max-writes-per-key opts 128)
                   :consistency-models [:strict-serializable]}) ;; Change this for a more fair assesment.
        ;; Our friendly neighbourhood nemesis, and its corresponding properties.
        nemesis (db-nemesis/nemesis opts)]

    (merge tests/noop-test
           opts
           {:name "redislike"
            :os   debian/os
            :db   db
            ;; inject nodes as a list of n1 - nN. Bit easier than writing --nodes=n1,n2,etc...
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
;; CLI validation utilites
(def possible-faults
  "Types of faults a nemesis can create."
  #{:fail-over :partition :packet :kill :pause :clock})

(def special-faults
  "A map of special faults names to collections of faults"
  {:none      []
   :standard  [:fail-over :partition :packet :kill :pause :clock]
   :all       [:fail-over :partition :packet :kill :pause :clock]})

(defn parse-fault-spec
  "Takes a comma-separated faults string and returns a collection of keyword
  faults."
  [spec]
  (->> (str/split spec #",")
       (map keyword)
       (mapcat #(get special-faults % [%]))
       (into [])))

;; CLI options
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

   [nil "--key-count INT" "Number of keys in the test"
    :parse-fn parse-long
    :default 12]

   ["-p" "--port INT" "DB node port"
    :default 7000]

   [nil "--faults FAULTS" "A comma-separated list of nemesis faults to enable"
    :parse-fn parse-fault-spec
    :validate [(partial every? (into possible-faults (keys special-faults)))
               (str "Faults must be one of " possible-faults " or "
                    (cli/one-of special-faults))]]

   [nil "--replicas INT" "Replicas per primary"
    :parse-fn parse-long
    :default 1]

   ["-d" "--database NAME" "The database to test"
    :parse-fn keyword
    :validate [(partial contains? db-def/db-defaults) (cli/one-of (keys db-def/db-defaults))]
    :default :redis]

   [nil "--max-txn-length INT" "What's the most operations we can execute per transaction?"
    :parse-fn parse-long
    :validate [pos? "must be positive"]]

   ["-r" "--rate HZ" "Approximate number of requests per second per thread"
    :default 10
    :parse-fn read-string
    :validate [#(and (number? %) (pos? %)) "must be a positive number"]]])

;; Start here:
(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn redis-test
                                         :opt-spec cli-opts})
                   (cli/serve-cmd))
            args))
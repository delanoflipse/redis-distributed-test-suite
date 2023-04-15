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


(defn as-node-host [num] (str "n" (+ num 1)))

(defn redis-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts#]
  (let [opts (merge opts# (db-def/db-opts! opts#))
        db (db-def/db)
        opts     (assoc opts :db db)
        workload (append/test
                  {; Exponentially distributed, so half of the time it's gonna
                    ; be one key, 3/4 of ops will use one of 2 keys, 7/8 one of
                    ; 3 keys, etc.
                   :key-count          (:key-count opts (:count opts 6))
                   :min-txn-length     1
                   :max-txn-length     (:max-txn-length opts 1)
                   :max-writes-per-key (:max-writes-per-key opts 128)
                   :consistency-models [:strict-serializable]})
        nemesis (db-nemesis/nemesis opts)]

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

(def nemeses
  "Types of faults a nemesis can create."
  #{:fail-over :partition :packet :kill :pause :clock})

(def special-nemeses
  "A map of special nemesis names to collections of faults"
  {:none      []
   :standard  [:fail-over :partition :packet :kill :pause :clock]
   :all       [:fail-over :partition :packet :kill :pause :clock]})

(defn parse-nemesis-spec
  "Takes a comma-separated nemesis string and returns a collection of keyword
  faults."
  [spec]
  (->> (str/split spec #",")
       (map keyword)
       (mapcat #(get special-nemeses % [%]))
       (into [])))

(defn as-keyword
  "Convert to keyword"
  [stringlike]
  (keyword stringlike))

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

   [nil "--faults FAULTS" "A comma-separated list of nemesis faults to enable"
    :parse-fn parse-nemesis-spec
    :validate [(partial every? (into nemeses (keys special-nemeses)))
               (str "Faults must be one of " nemeses " or "
                    (cli/one-of special-nemeses))]]

   [nil "--replicas INT" "Replicas per primary"
    :parse-fn parse-long
    :default 1]

   ["-d" "--database NAME" "redis or keydb"
    :parse-fn as-keyword
    :validate [(partial contains? db-def/db-defaults) (cli/one-of (keys db-def/db-defaults))]
    :default :redis]

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
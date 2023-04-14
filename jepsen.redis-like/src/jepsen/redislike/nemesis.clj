(ns jepsen.redislike.nemesis
  (:require [clojure.tools.logging :refer [info warn]]
            [elle.list-append :as a]
            [jepsen
             [control :as c]
             [checker :as checker]
             [cli :as cli]
             [generator :as gen]
             [nemesis :as nemesis]
             [tests :as tests]]
            [jepsen.checker.timeline :as timeline]
            [jepsen.redislike
             [database :as db-def]
             [client :as db-client]
             [util :as p-util]]
            [jepsen.nemesis [time :as nt]
             [combined :as nc]]
            [jepsen.os.debian :as debian]
            [jepsen.tests.cycle.append :as append])
  (:use clojure.pprint))

(defn my-nemesis
  "Adds and removes nodes from the cluster."
  []
  (reify nemesis/Nemesis
    (setup! [this test] this)

    (invoke! [this test op]
      (assoc op :value
             (case (:f op)
               :hold   nil
               :fail-over (let [node (:node op)] ;; (rand-nth (:nodes test))
                            (info node)
                            (c/with-session node (get (:sessions test) node)
                              (c/cd db-def/working-dir
                                    (c/exec (c/lit (str "./" (:db-cli test) " -c -p 7000 cluster failover"))))))
               :del-node (let [node (:node op)]
                           (c/with-session node (get (:sessions test) node)
                             (c/cd db-def/working-dir
                                   (let [node-id (c/exec (c/lit (str "./" (:db-cli test) " -c -p 7000 cluster myid")))]
								;;(info node-id)
								;;(pprint test)
								;;(info (:node op))
                                     (c/exec (c/lit (str "./" (:db-cli test) " --cluster del-node n1:7000")) node-id)))))
               :add-node (let [node (:node op)]
                           (c/with-session node (get (:sessions test) node)
                             (c/cd db-def/working-dir
                                   (c/exec (c/lit (str "./" (:db-cli test) " --cluster add-node localhost:7000")) (p-util/node-url "n1" 7000)))))
               :cluster-nodes (let [node (:node op)]
                                (c/with-session node (get (:sessions test) node)
                                  (c/cd db-def/working-dir
                                        (info (c/exec (c/lit "./redis-cli -c -p 7000 cluster nodes")))))))))

    (teardown! [this test])))

(defn nemesisgenerator []
  (flatten (repeatedly #(identity [(gen/sleep 1)
                                   {:type :info, :f :start-p-half}
                                   (gen/sleep 1)
                                   {:type :info, :f :fail-over, :node (rand-nth ["n1", "n2", "n4", "n5", "n6"])}
                                   (gen/sleep 1)
                                   {:type :info, :f :stop-p-half}
                                   (gen/sleep 1)
                                   {:type :info, :f :fail-over, :node (rand-nth ["n1", "n2", "n4", "n5", "n6"])}
                                   (gen/sleep 1)
                                   {:type :info, :f :start-p-node}
                                   (gen/sleep 1)
                                   {:type :info, :f :fail-over, :node (rand-nth ["n1", "n2", "n4", "n5", "n6"])}
                                   (gen/sleep 1)
                                   {:type :info, :f :stop-p-node}
				;;{:type :info, :f :del-node, :node "n4"}
				;;(gen/sleep 1)
				;;{:type :info, :f :cluster-nodes, :node "n1"}
				;;(gen/sleep 1)
				;;{:type :info, :f :add-node, :node "n4"}
				;;(gen/sleep 10)
                                   ]))))

(defn package-for
  "Builds a combined package for the given options."
  [opts]
  (let [nem-opts (nc/compose-packages (nc/nemesis-packages opts))]  nem-opts))

(defn nemesis [opts db]
  (let [nem-pkg (package-for opts)] {:generator (:generator nem-pkg)
                                     :final-generator (:final-generator nem-pkg)
                                     :perf (:perf nem-pkg)
                                     :nemesis (:nemesis nem-pkg)}))
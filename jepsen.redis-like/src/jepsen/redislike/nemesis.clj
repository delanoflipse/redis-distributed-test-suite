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
            [jepsen.os.debian :as debian]
            [jepsen.tests.cycle.append :as append]			
    )
	(:use clojure.pprint)
)

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
							(c/exec (c/lit "./redis-cli -c -p 7000 cluster failover"))
						)
					)
				)
				:del-node (let [node (:node op)]
					(c/with-session node (get (:sessions test) node)
						(c/cd db-def/working-dir
							(let [node-id (c/exec (c/lit "./redis-cli -c -p 7000 cluster myid"))]
								;;(info node-id)
								;;(pprint test)
								;;(info (:node op))
								(c/exec (c/lit "./redis-cli --cluster del-node n1:7000") node-id)
							)
						)
					)
			    )
				:add-node (let [node (:node op)]
					(c/with-session node (get (:sessions test) node)
						(c/cd db-def/working-dir
							(let [node-id (c/exec (c/lit "./redis-cli -c -p 7000 cluster myid"))]
								;;(info node-id)
								;;(info "add")
								(c/exec (c/lit "./redis-cli --cluster add-node localhost:7000") (p-util/node-url "n1" 7000)) ;; need node ip
							)
						)
					)
				)
				:cluster-nodes (let [node (:node op)]
					(c/with-session node (get (:sessions test) node)
						(c/cd db-def/working-dir
							(info (c/exec (c/lit "./redis-cli -c -p 7000 cluster nodes")))
						)
					)
				)
            )
		)
	)
	
	(teardown! [this test]))
)
			
(defn nemesisgenerator []
	(cycle [	(gen/sleep 2)
                {:type :info, :f :start}
                (gen/sleep 2)
                {:type :info, :f :stop}
				(gen/sleep 2)
				{:type :info, :f :del-node, :node "n4"}
				(gen/sleep 1)
				{:type :info, :f :cluster-nodes, :node "n1"}
				(gen/sleep 1)
				{:type :info, :f :add-node, :node "n4"}
				(gen/sleep 10)
			]
	)
)

(defn nemesisoptions []
    (nemesis/compose
		{
		    #{:start :stop} (nemesis/partition-random-halves)
		    #{:hold :fail-over :del-node :add-node :cluster-nodes} (my-nemesis)
		}
	)
)
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
             [client :as db-client]]
            [jepsen.os.debian :as debian]
            [jepsen.tests.cycle.append :as append]			
            ))

(defn my-nemesis
  "Adds and removes nodes from the cluster."
  []
  (reify nemesis/Nemesis
    (setup! [this test] this)

    (invoke! [this test op]
		(info "run nemesis")
        (assoc op :value
             (case (:f op)
               :hold   nil
			   :fail-over (do
			        (c/cd db-def/working-dir
                        ;; (c/exec (c/lit "./redis-cli --cluster n6 CLUSTER FAILOVER FORCE")) ;; fails nomatter the command
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
				{:type :info, :f :fail-over}
				(gen/sleep 10)
			]
	)
)

(defn nemesisoptions []
    (nemesis/compose
		{
		    #{:start :stop} (nemesis/partition-random-halves)
		    #{:hold :fail-over} (my-nemesis)
		}
	)
)
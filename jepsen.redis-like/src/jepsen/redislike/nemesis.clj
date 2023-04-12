(ns jepsen.redislike.nemesis
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
		(case (:f op)
			:mytest
			(do
				(info "nemesis sayes hi")
			)
		)
	)
	
	(teardown! [this test]))
)
			
(defn nemesisgenerator []
	(cycle [(gen/sleep 5)
            {:type :info, :f :split-start}
            (gen/sleep 5)
            {:type :info, :f :split-stop}])
			{:type :info, :f :mytest}
)

(defn nemesisoptions []
    (nemesis/compose {
	          {:split-start :start
               :split-stop  :stop} (nemesis/partition-random-halves)
              {:ring-start  :start
               :ring-stop2  :stop} (nemesis/partition-majorities-ring)
			  {:mytest      :mytest} (my-nemesis)
    })
)
(ns read
	(:require [elle.list-append :as a]
            [jepsen.history :as h]
			[clojure.java.io :as io]
			[clojure.edn :as edn]
	)
	(:use clojure.pprint)
)

(defn load-edn
	"load a jepsen history edn"
	[src]
	(with-open [rdr (clojure.java.io/reader "test_history.edn")]
		(into []
			(for [line (line-seq rdr)]
				(edn/read-string line)
			)
		)
	)
)

(defn main [opts]
	(pprint opts)
	(pprint
		(a/check
			{:consistency-models [:serializable], :directory "out"}
			(h/history (load-edn (:file opts)))
		)
	)
)

(ns redis-plain.core
    (:require [clojure.tools.logging :refer :all]
              [clojure.string :as str]
              [jepsen [cli :as cli]
                      [control :as c]
                      [db :as db]
                      [tests :as tests]]
              [jepsen.control.util :as cu]
              [jepsen.os.debian :as debian]
    )
)

(def dir "/opt/redis")

(defn db
  "redis DB for a particular version."
  [version]
  (reify db/DB
    (setup! [_ test node]
      (info node "installing redis" version)
      (c/su
        (let [url (str "https://packages.redis.io/redis-stack/redis-stack-server-" version
                       ".focal.x86_64.tar.gz")]
          (cu/install-archive! url dir)
        )
      )
    )

    (teardown! [_ test node]
      (info node "tearing down redis")
    )
  )
)

(defn redis-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:name "redis"
          :os debian/os
          :db (db "6.2.6-v6")
          :pure-generators true
         }
  )
)

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn redis-test})
                   (cli/serve-cmd)
	    )
            args
  )
)

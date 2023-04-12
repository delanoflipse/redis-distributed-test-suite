(ns jepsen.redislike.client
  (:require [clojure.tools.logging :refer [info warn]]
            [clojure.string :as str]
            [taoensso.carmine :as car :refer [wcar]]
            [taoensso.carmine [connections :as conn]]
            [jepsen
             [cli :as cli]
             [util :as util :refer [parse-long]]
             [client :as jclient]]
            [slingshot.slingshot :refer [try+ throw+]]
            [jepsen.redislike.util :as p-util]
            [jepsen.redislike.jedis :as jedis]
            [jepsen.redislike.database :as db-def]))

(defrecord RedisClient [conn]
  ;; TODO: error handling
  jepsen.client/Client
  (open! [this test node]
    (assoc this :conn (jedis/connect! (:nodes test) 7000)))

  (close! [this test]
    (.close conn))

  (setup! [_ test])

  (invoke! [this test op]
    (case (:f op)
      :read (assoc op :type :ok, :value (mapv parse-long (.lrange conn "foo" 0 -1)))
      :write (do
               (.rpush conn "foo" (into-array String [(str (:value op))]))
               (assoc op :type :ok))))

  (teardown! [_ test]))

(ns jepsen.redislike.client
  (:require [clojure.tools.logging :refer [info warn]]
            [clojure.string :as str]
            [taoensso.carmine :as car :refer [wcar]]
            [taoensso.carmine [connections :as conn]]
            [jepsen
             [cli :as cli]
             [client :as jclient]]
            [slingshot.slingshot :refer [try+ throw+]]
            [jepsen.redislike.util :as p-util]
            [jepsen.redislike.jedis :as jedis]
            [jepsen.redislike.database :as db-def]))

(defrecord RedisClient [conn]
  jepsen.client/Client
  (open! [this test node]
    (assoc this :conn (jedis/connect! (:nodes test) 7000)))

  (close! [this test]
    (.close conn))

  (setup! [_ test])

  (invoke! [this test op]
    (case (:f op)
      :read (assoc op :type :ok, :value (.get conn "foo"))
      :write (do
               (.set conn "foo" (:value op))
               (assoc op :type :ok))))

  (teardown! [_ test]))

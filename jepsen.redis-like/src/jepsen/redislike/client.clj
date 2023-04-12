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
            [jepsen.redislike.database :as db-def]))



(defrecord SingleConnectionPool [conn]
  conn/IConnectionPool
  (get-conn [_ spec] conn)

  (release-conn [_ conn])

  (release-conn [_ conn exception])

  java.io.Closeable
  (close [_] (conn/close-conn conn)))

(defn open-con
  "Opens a connection to a node. Our connections are Carmine IConnectionPools.
  Options are merged into the conn pool spec."
  ([node]
   (open-con node {}))
  ([node opts]
   (let [spec (merge {:host       node
                      :port       db-def/node-port
                      :timeout-ms 10000}
                     opts)
         seed-pool (conn/conn-pool :none)
         conn      (conn/get-conn seed-pool spec)]
     {:pool (SingleConnectionPool. conn)
      ; See with-txn
      :in-txn? (atom false)
      :spec spec})))

(defn close-con!
  "Closes a connection to a node."
  [^java.io.Closeable conn]
  (.close (:pool conn)))


(defrecord RedisClient [conn]
  jepsen.client/Client
  (open! [this test node]

    (assoc this :conn (open-con node)))

  (close! [this test]
          (close-con! conn))

  (setup! [_ test])

  (invoke! [this test op]
           (case (:f op)
             :read (assoc op :type :ok, :value (wcar conn (car/get "foo")))))

  (teardown! [_ test])

  )

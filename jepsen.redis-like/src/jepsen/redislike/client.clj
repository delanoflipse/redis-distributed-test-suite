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

;; TODO: extend with actually occurring errors
(defmacro with-exceptions
  "Takes an operation, an idempotent :f set, and a body; evaluates body,
  converting known exceptions to failed ops."
  [op idempotent & body]
  `(let [crash# (if (~idempotent (:f ~op)) :fail :info)]
     (try+ ~@body
           (catch [:prefix :err] e#
             (condp re-find (.getMessage (:throwable ~'&throw-context))
               ; These two would ordinarily be our fault, but are actually
               ; caused by follower proxies mangling connection state.
               #"ERR DISCARD without MULTI"
               (assoc ~op :type crash#, :error :discard-without-multi)

               #"ERR MULTI calls can not be nested"
               (assoc ~op :type crash#, :error :nested-multi)

               (throw+)))
           (catch [:prefix :moved] e#
             (assoc ~op :type :fail, :error :moved))

           (catch [:prefix :nocluster] e#
             (assoc ~op :type :fail, :error :nocluster))

           (catch [:prefix :clusterdown] e#
             (assoc ~op :type :fail, :error :clusterdown))

           (catch [:prefix :notleader] e#
             (assoc ~op :type :fail, :error :notleader))

           (catch [:prefix :timeout] e#
             (assoc ~op :type crash# :error :timeout))

           (catch java.io.EOFException e#
             (assoc ~op :type crash#, :error :eof))

           (catch java.net.ConnectException e#
             (assoc ~op :type :fail, :error :connection-refused))

           (catch java.net.SocketException e#
             (assoc ~op :type crash#, :error [:socket (.getMessage e#)]))

           (catch java.net.SocketTimeoutException e#
             (assoc ~op :type crash#, :error :socket-timeout)))))

(defrecord RedisClient [conn]
  jepsen.client/Client
  (open! [this test node]
    (assoc this :conn (jedis/connect! (:nodes test) 7000)))

  (close! [this test]
    (.close conn))

  (setup! [_ test])

  (invoke! [this test op]
    (with-exceptions op #{}
      (case (:f op)
        :read (assoc op :type :ok, :value (mapv parse-long (.lrange conn "foo" 0 -1)))
        :write (do
                 (.rpush conn "foo" (into-array String [(str (:value op))]))
                 (assoc op :type :ok)))))

  (teardown! [_ test]))

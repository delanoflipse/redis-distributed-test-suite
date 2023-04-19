(ns jepsen.redislike.client
  (:require [clojure.tools.logging :refer [info warn]]
            [jepsen
             [util :as util :refer [parse-long]]
             [client :as jclient]]
            [slingshot.slingshot :refer [try+ throw+]]
            [jepsen.redislike.jedis :as jedis]))

;; This macro was taken from https://github.com/jepsen-io/redis/blob/54ab5c10ace229fc780e43e188ef1b64c3a32ee4/src/jepsen/redis/client.clj
;; Some of these errors are probably not used
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

           (catch redis.clients.jedis.exceptions.JedisClusterException e#
             (case (.getMessage e#)
               ;; Known error, SHOULD indicate a failure to write.
               "CLUSTERDOWN The cluster is down" (assoc ~op :type :fail, :error :clusterdown)
               (assoc ~op :type :info, :error (str "JedisClusterException:" (.getMessage e#)))))

           (catch redis.clients.jedis.exceptions.JedisClusterOperationException e#
             (assoc ~op :type :info, :error (str "JedisClusterOperationException:" (.getMessage e#))))

           (catch java.net.ConnectException e#
             (assoc ~op :type :fail, :error :connection-refused))

           (catch java.net.SocketException e#
             (assoc ~op :type crash#, :error [:socket (.getMessage e#)]))

           (catch java.net.SocketTimeoutException e#
             (assoc ~op :type crash#, :error :socket-timeout)))))

;; Adjusted from https://github.com/jepsen-io/redis/blob/54ab5c10ace229fc780e43e188ef1b64c3a32ee4/src/jepsen/redis/append.clj
;; The older implementation accounted for transactions, which Redis cluster only supports on same-node keys.
;; Which was way too much hassle to get implemented. Furthermore, JedisCluster does not support .multi() 
(defn apply-operation!
  "Apply a single operation in a (Elle-formatted) transaction."
  [conn [operation key-name value :as op-def]]
  (case operation
    ;; Read a list of strings from a key. Convert them to numbers.
    ;; we replace the nil value with the actual number list.
    :r      [operation key-name (mapv parse-long (.lrange conn (str key-name) 0 -1))]
    ;; Right append a (string converted) list of numbers to a key.
    ;; The clojure<->java interop did not like vararg functions, that explains the single entry String Array.
    :append (do
              (.rpush conn (str key-name) (into-array String [(str value)]))
              ;; return the original operation (we only get here if it succeeds due to the macro)
              op-def)))

;; Redis client using Jedis
(defrecord RedisClient [conn]
  jepsen.client/Client
  (open! [this test node]
    (assoc this :conn (jedis/connect! node test)))

  (close! [this test]
    (.close conn))

  (setup! [_ test])

  (invoke! [this test op]
    (with-exceptions op #{}
      (->>
       ;; take the transaction
       (:value op)
       ;; apply every operation (well, only one) 
       (mapv apply-operation! (repeat conn))
       ;; return and associate
       (assoc op :type :ok, :value))))

  (teardown! [_ test]))

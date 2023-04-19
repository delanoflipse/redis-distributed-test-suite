(ns jepsen.redislike.jedis
  (:require [clojure.tools.logging :refer [info warn]]
            [jepsen.control.net :as net])
  (:import [redis.clients.jedis HostAndPort JedisCluster]))

(defn as-host-and-port! [node port] (HostAndPort. (net/ip node) port))

(defn connect! [node test]
  (let [c (apply hash-set [(as-host-and-port! node (:port test))])]
    (JedisCluster. c (int (:client-timeout test)) (int (:client-max-retries test)))))

;; We weren't really sure what Jedis does, but we expected it to be a fairer test if we didn't give Jedis the knowledge about the whole cluster
;; As this is allowed by the Cluster specification.
;; However, use this method if you want the clients to know about all nodes.
;; (defn connect! [node test]
;;    (let [c (apply hash-set (map as-host-and-port! (:nodes test) (repeat (:port test))))]
;;      (JedisCluster. c (int (:client-timeout test))  (int (:client-max-retries test)) )))
(ns jepsen.redislike.jedis
  (:require [clojure.tools.logging :refer [info warn]]
             [jepsen.control.net :as net])
  (:import [redis.clients.jedis HostAndPort JedisCluster]))

(defn as-host-and-port! [node port] (HostAndPort. (net/ip node) port))

(defn connect! [node test]
   (let [c (apply hash-set [(as-host-and-port! node (:port test))])]
     (JedisCluster. c)))

;; (defn connect! [node test]
;;    (let [c (apply hash-set (map as-host-and-port! (:nodes test) (repeat (:port test))))]
;;      (JedisCluster. c)))
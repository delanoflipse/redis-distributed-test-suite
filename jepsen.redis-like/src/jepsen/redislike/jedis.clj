(ns jepsen.redislike.jedis
  (:require [clojure.tools.logging :refer [info warn]]
             [jepsen.control.net :as net])
  (:import [redis.clients.jedis HostAndPort JedisCluster]))

(defn as-host-and-port! [node port] (HostAndPort. (net/ip node) port))

(defn connect! [nodes port]
   (let [c (apply hash-set (map as-host-and-port! nodes (repeat port)))]
     (JedisCluster. c)))
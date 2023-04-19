(ns jepsen.redislike.util
  (:require [jepsen.control.net :as net]))

;; To prevent circular depencies, this lonely utility resides here
(defn node-url "Get the full url to a node" [node-name port] (str (net/ip node-name) ":" port))
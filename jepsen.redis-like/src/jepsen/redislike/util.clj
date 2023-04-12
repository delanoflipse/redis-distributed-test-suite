(ns jepsen.redislike.util
  (:require [jepsen.control.net :as net]))

(defn node-url [node-name port] (str (net/ip node-name) ":" port))
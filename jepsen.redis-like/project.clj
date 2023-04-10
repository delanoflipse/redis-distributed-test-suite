(defproject jepsen.redis-like "0.1.0-SNAPSHOT"
  :description "A Jepsen test Redis-like DBs and setups"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main jepsen.redis-like
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [jepsen "0.2.7"]
                 [com.taoensso/carmine "3.2.0"]])

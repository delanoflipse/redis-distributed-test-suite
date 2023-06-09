(defproject jepsen.redislike "0.1.0-SNAPSHOT"
  :description "A Jepsen test for Redis-like DBs and setups"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main jepsen.redislike.core
  :jvm-opts ["-Xmx4g" "-Djava.awt.headless=true"]
  :repl-options {:init-ns jepsen.redislike.core}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [jepsen "0.2.7"]
                 [redis.clients/jedis "4.3.0"]
                 [elle "0.1.5"]])

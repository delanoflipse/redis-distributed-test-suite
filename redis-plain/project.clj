(defproject redis-plain "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [jepsen "0.3.1"]
		 [com.taoensso/carmine "3.3.0-alpha7"]]
  :repl-options {:init-ns redis-plain.core}
  :main redis-plain.core)

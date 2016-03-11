(defproject pedestal-api-example "0.1.0-SNAPSHOT"
  :description "An example project for pedestal-api"
  :url "https://github.com/oliyh/pedestal-api"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [pedestal-api "0.1.0"]
                 [io.pedestal/pedestal.service "0.4.1"]
                 [io.pedestal/pedestal.jetty "0.4.1"]

                 [ch.qos.logback/logback-classic "1.1.6" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.18"]
                 [org.slf4j/jcl-over-slf4j "1.7.18"]
                 [org.slf4j/log4j-over-slf4j "1.7.18"]
                 [org.clojure/tools.logging "0.3.1"]]
  :main ^:skip-aot pedestal_api_example.server
  :target-path "target/%s"
  :uberjar-name "pedestal-api-example-standalone.jar"
  :profiles {:uberjar {:aot :all}
             :dev {:repl-options {:init-ns pedestal-api-example.server}}})

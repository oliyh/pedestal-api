(defproject pedestal-api "0.3.5"
  :description "A batteries-included API for Pedestal using Swagger"
  :url "https://github.com/oliyh/pedestal-api"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[oliyh/route-swagger "0.1.5"]]
  :repl-options {:init-ns user}
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.1"]
                                       [io.pedestal/pedestal.service "0.5.8"]]}
             :dev {:source-paths ["dev" "example"]
                   :resources ["dev-resources"]
                   :dependencies [[potemkin "0.4.5"]
                                  [org.clojure/tools.namespace "1.1.0"]
                                  [io.pedestal/pedestal.jetty "0.5.8"]
                                  [clj-http "3.11.0"]

                                  [ch.qos.logback/logback-classic "1.2.3"
                                   :exclusions [org.slf4j/slf4j-api]]
                                  [org.slf4j/jcl-over-slf4j "1.7.30"]
                                  [org.slf4j/jul-to-slf4j "1.7.30"]
                                  [org.slf4j/log4j-over-slf4j "1.7.30"]]}}

  ;; for heroku deployment of example
  :min-lein-version "2.0.0"
  :plugins [[lein-sub "0.3.0"]]
  :sub ["example"]
  :aliases {"uberjar" ["sub" "uberjar"]}
  :uberjar-name "")

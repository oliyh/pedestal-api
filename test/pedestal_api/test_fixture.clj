(ns pedestal-api.test-fixture
  (:require [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :refer [url-for-routes]]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(def port 8082)

(defn service [routes]
  {:env                      :dev
   ::bootstrap/routes        routes
   ::bootstrap/router        :linear-search
   ::bootstrap/resource-path "/public"
   ::bootstrap/type          :jetty
   ::bootstrap/join?         false})

(defn url-for [routes handler & args]
  (apply (url-for-routes routes
                         :absolute? true
                         :request {:scheme "http"
                                   :server-name "localhost"
                                   :server-port port})
         handler
         args))

(defn with-server [routes]
  (fn [f]
    (let [server (bootstrap/start (bootstrap/create-server
                                   (-> (service routes)
                                       (merge {::bootstrap/port port})
                                       (bootstrap/default-interceptors))))]
      (try
        (f)
        (finally (bootstrap/stop server))))))

(defn transit-read-bytes [bytes type]
  (transit/read (transit/reader (ByteArrayInputStream. bytes) type)))

(defn transit-write-bytes [body type]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out type)]
    (transit/write writer body)
    (.toByteArray out)))

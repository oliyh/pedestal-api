(ns pedestal-api.sse-test
  (:require [clojure.test :refer :all]
            [pedestal-api
             [core :as api]
             [test-fixture :as tf]
             [sse-client :as sse-client]]
            [schema.core :as s]
            [io.pedestal.http.sse :as sse]
            [clojure.core.async :as a])
  (:import [java.io InputStream]))

(defn- initialise-stream [event-channel context]
  (a/go-loop [i 0]
    (a/>! event-channel {:data i})
    (a/<! (a/timeout 10))
    (recur (inc i))))

(def events
  (api/annotate
   {:summary "SSE event stream"
    :description "Broadcasts random numbers"
    :parameters {}}
   (assoc (sse/start-event-stream initialise-stream)
          :name ::events)))

(s/with-fn-validation
  (api/defroutes routes
    {}
    [[["/" ^:interceptors [api/error-responses
                           (api/negotiate-response)
                           (api/body-params)
                           api/common-body
                           (api/coerce-request)
                           (api/validate-response)]
       ["/events" {:get events}]
       ["/swagger.json" {:get api/swagger-json}]]]]))

(use-fixtures :once (tf/with-server routes))

(deftest plain-sse-test
  (testing "plain events are just strings"
    (let [events (a/map :data [(sse-client/connect (tf/url-for routes ::events))])]
      (is (= ["0" "1" "2" "3" "4" "5" "6" "7" "8" "9"]
             (first (a/alts!! [(a/into [] (a/take 10 events)) (a/timeout 1000)])))))))

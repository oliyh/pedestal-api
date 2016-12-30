(ns pedestal-api.sse-test
  (:require [clojure.test :refer :all]
            [pedestal-api
             [core :as api]
             [test-fixture :as tf]
             [sse-client :as sse-client]
             [content-negotiation :as cn]]
            [schema.core :as s]
            [io.pedestal.http.sse :as sse]
            [io.pedestal.interceptor :refer [interceptor]]
            [clojure.core.async :as a]
            [clj-http.client :as http]
            [cheshire.core :as json])
  (:import [java.io InputStream]))

(defn- initialise-stream [event-channel context]
  (a/go-loop [i 0]
    (a/>! event-channel {:data {:content i}})
    (a/<! (a/timeout 10))
    (recur (inc i))))

;; it might be better if we could use a slightly less intrusive interceptor like this one that can just
;; target the channel with the raw data on. unfortunately the channel is not accessible and the data
;; is written to it already serialised

(def json-event-interceptor
  {:name ::json-event-interceptor
   :leave (fn [{:keys [request] :as ctx}]
            (def c ctx)
            (-> ctx
                (assoc-in [:response :headers "Content-Type"] "json/event-stream; charset=UTF-8")
                (update-in [:response-channel] #(a/map (fn [event] (println "got an event!" event) event) [%]))))})


;; more intrusive method but gives full control

(defn start-event-stream
  ([stream-ready-fn]
   (start-event-stream stream-ready-fn 10 10))
  ([stream-ready-fn heartbeat-delay]
   (start-event-stream stream-ready-fn heartbeat-delay 10))
  ([stream-ready-fn heartbeat-delay bufferfn-or-n]
   (start-event-stream stream-ready-fn heartbeat-delay bufferfn-or-n {}))
  ([stream-ready-fn heartbeat-delay bufferfn-or-n opts]
   (interceptor
    {:name (keyword (str (gensym "pedestal-api.sse/start-event-stream")))
     :enter (fn [{:keys [request] :as ctx}]

              ;; todo should have already had content negotiated by this point, should be able to use that
              ;; should be able to set produces and consumes on this interceptor

              (sse/start-stream
               (fn [out-ch context]
                 (let [event-ch (a/chan 1 (map (fn [event]
                                                 (let [e (if (map? event) event {:data event})]
                                                   (update e :data json/encode)))))]
                   (a/pipe event-ch out-ch)
                   (stream-ready-fn event-ch context)))
               ctx heartbeat-delay bufferfn-or-n opts))
     :leave (fn [ctx]
              (assoc-in ctx [:response :headers "Content-Type"] "json/event-stream; charset=UTF-8"))})))

(def plain-events
  (api/annotate
   {:summary "Plain-text event stream"
    :description "Broadcasts random numbers"
    :parameters {}}
   (assoc (sse/start-event-stream initialise-stream)
          :name ::plain-events)))

(def json-events
  (api/annotate
   {:summary "JSON event stream"
    :description "Broadcasts random numbers"
    :parameters {}}
   (assoc (start-event-stream initialise-stream)
          :name ::json-events)))

(s/with-fn-validation
  (api/defroutes routes
    {}
    [[["/" ^:interceptors [api/error-responses
                           (api/negotiate-response)
                           (api/body-params)
                           api/common-body
                           (api/coerce-request)
                           (api/validate-response)]
       ["/plain-events" {:get plain-events}]
       ["/json-events" {:get json-events}]
       ["/swagger.json" {:get api/swagger-json}]]]]))

(use-fixtures :once (tf/with-server routes))

(defn- n-vec [n ch]
  (first (a/alts!! [(a/into [] (a/take n ch)) (a/timeout 1000)])))

(deftest plain-sse-test
  (testing "plain events are just strings"
    (is (= "text/event-stream; charset=UTF-8"
           (get-in (http/get (tf/url-for routes ::plain-events) {:as :stream})
                   [:headers "Content-Type"])))

    (let [events (a/map :data [(sse-client/connect (tf/url-for routes ::plain-events))])]
      (is (= ["{:content 0}" "{:content 1}" "{:content 2}" "{:content 3}" "{:content 4}"]
             (n-vec 5 events))))))

(deftest json-sse-test
  (testing "json events keep numbers as numbers"
    (is (= "json/event-stream; charset=UTF-8"
           (get-in (http/get (tf/url-for routes ::json-events) {:headers {"Accept" "json/event-stream"}
                                                                :as :stream})
                   [:headers "Content-Type"])))

    (let [events (a/map :data [(sse-client/connect (tf/url-for routes ::json-events))])]
      (is (= [{:content 0} {:content 1} {:content 2} {:content 3} {:content 4}]
             (map #(json/decode % keyword) (n-vec 5 events)))))))

(ns pedestal-api.request-params-test
  (:require [clojure.test :refer [deftest testing is]]
            [pedestal-api.request-params :refer [body-params common-body]]
            [pedestal-api.error-handling :refer [error-responses]]
            [io.pedestal.interceptor.chain :refer [execute]]
            [clojure.java.io :as io]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayOutputStream]))

(defn transit-encode [body type]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out type)]
    (transit/write writer body)
    (io/input-stream (.toByteArray out))))

(defn bad-body []
  (io/input-stream (.getBytes "{\"foo\"\"}")))

(def bad-request-response
  {:status 400
   :body "Cannot deserialise body"})

(deftest body-parsing-test
  (let [enter (fn [ctx]
                (execute ctx [error-responses (body-params) common-body]))]

    (testing "json"
      (testing "can parse"
        (is (= {:foo "bar"}
               (get-in (enter {:request {:body (io/input-stream (.getBytes "{\"foo\": \"bar\"}"))
                                         :content-type "application/json"}})
                       [:request :body-params]))))

      (testing "and reject"
        (is (= bad-request-response
               (:response (enter {:request {:body (bad-body)
                                            :content-type "application/json"}}))))))

    (testing "edn"
      (testing "can parse"
        (is (= {:foo "bar"}
               (get-in (enter {:request {:body (io/input-stream (.getBytes (pr-str {:foo "bar"})))
                                         :content-type "application/edn"}})
                       [:request :body-params]))))

      (testing "and reject"
        (is (= bad-request-response
               (:response (enter {:request {:body (bad-body)
                                            :content-type "application/edn"}}))))))

    (testing "transit+json"
      (testing "can parse"
        (is (= {:foo "bar"}
               (get-in (enter {:request {:body (transit-encode {:foo "bar"} :json)
                                         :content-type "application/transit+json"}})
                       [:request :body-params]))))

      (testing "and reject"
        (is (= bad-request-response
               (:response (enter {:request {:body (bad-body)
                                            :content-type "application/transit+json"}}))))))

    (testing "transit+msgpack"
      (testing "can parse"
        (is (= {:foo "bar"}
               (get-in (enter {:request {:body (transit-encode {:foo "bar"} :msgpack)
                                         :content-type "application/transit+msgpack"}})
                       [:request :body-params])))))

    ;; cannot make a transit+json string that cannot be deserialised!
    ))

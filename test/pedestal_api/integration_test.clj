(ns pedestal-api.integration-test
  (:require [pedestal-api
             [core :as api]
             [helpers :refer [defhandler]]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.http.route :refer [url-for-routes]]
            [schema.core :as s]
            [clj-http.client :as http]
            [clojure.test :refer :all]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(s/defschema Pet
  {:name s/Str
   :type s/Str
   :age s/Int})

(def create-pet
  (api/annotate
   {:parameters {:body-params Pet}
    :responses {201 {:body {:pet Pet}}}}
   (interceptor
    {:name  ::create-pet
     :enter (fn [ctx]
              (assoc ctx :response
                     {:status 201
                      :body {:pet (get-in ctx [:request :body-params])}}))})))

(def get-all-pets
  (api/annotate
   {:parameters {:query-params {(s/optional-key :sort) (s/enum :asc :desc)}}
    :responses {200 {:body [Pet]}}}
   (interceptor
    {:name  ::get-all-pets
     :enter (fn [ctx]
              (assoc ctx :response
                     {:status 200
                      :body [{:name "Alfred"
                              :type "dog"
                              :age 6}]}))})))

(defhandler get-pet-by-name
  {:parameters {:path-params {:name s/Str}}
   :responses {200 {:body Pet}}}
  [{:keys [path-params]}]
  {:status 200
   :body {:name (:name path-params)
          :type "dog"
          :age 6}})

(api/defroutes routes
  {}
  [[["/" ^:interceptors [api/error-responses
                         (api/negotiate-response)
                         (api/body-params)
                         api/common-body
                         (api/coerce-request)
                         (api/validate-response)]
     ["/pets"
      {:get get-all-pets
       :post create-pet}
      ["/:name" {:get get-pet-by-name}]]
     ["/swagger.json" {:get api/swagger-json}]]]])

(def service
  {:env                      :dev
   ::bootstrap/routes        #(deref #'routes)
   ::bootstrap/router        :linear-search
   ::bootstrap/resource-path "/public"
   ::bootstrap/type          :jetty
   ::bootstrap/join?         false})

(def port 8082)

(defn url-for [handler & args]
  (apply (url-for-routes routes
                         :absolute? true
                         :request {:scheme "http"
                                   :server-name "localhost"
                                   :server-port port})
         handler
         args))

(defn with-server [f]
  (let [server (bootstrap/start (bootstrap/create-server
                                 (-> service
                                     (merge {::bootstrap/port port})
                                     (bootstrap/default-interceptors))))]
    (try
      (f)
      (finally (bootstrap/stop server)))))

(defn transit-read-bytes [bytes type]
  (transit/read (transit/reader (ByteArrayInputStream. bytes) type)))

(defn transit-write-bytes [body type]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out type)]
    (transit/write writer body)
    (.toByteArray out)))

(use-fixtures :once with-server)

(deftest can-get-content-test

  ;;json
  (let [response (http/get (url-for ::get-all-pets))]
    (is (= 200 (:status response)))
    (is (= "application/json;charset=UTF-8" (get-in response [:headers "Content-Type"])))
    (is (= [{:name "Alfred"
             :type "dog"
             :age 6}]
           (json/decode (:body response) keyword))))

  ;; edn
  (let [response (http/get (url-for ::get-all-pets) {:headers {"Accept" "application/edn"}})]
    (is (= 200 (:status response)))
    (is (= "application/edn;charset=UTF-8" (get-in response [:headers "Content-Type"])))
    (is (= [{:name "Alfred"
             :type "dog"
             :age 6}]
           (edn/read-string (:body response)))))

  ;; transit+json
  (let [response (http/get (url-for ::get-all-pets) {:headers {"Accept" "application/transit+json"}})]
    (is (= 200 (:status response)))
    (is (= "application/transit+json;charset=UTF-8" (get-in response [:headers "Content-Type"])))
    (is (= [{:name "Alfred"
             :type "dog"
             :age 6}]
           (transit-read-bytes (.getBytes (:body response)) :json))))

  (let [response (http/get (url-for ::get-all-pets) {:headers {"Accept" "application/transit+msgpack"}
                                                     :as :byte-array})]
    (is (= 200 (:status response)))
    (is (= "application/transit+msgpack;charset=UTF-8" (get-in response [:headers "Content-Type"])))
    (is (= [{:name "Alfred"
             :type "dog"
             :age 6}]
           (transit-read-bytes (:body response) :msgpack)))))

(deftest can-submit-content-test

  (let [pet {:name "Alfred"
             :type "dog"
             :age 6}]
    ;;json
    (let [response (http/post (url-for ::create-pet) {:body (json/encode pet)
                                                      :headers {"Content-Type" "application/json"
                                                                "Accept" "application/json"}})]
      (is (= 201 (:status response)))
      (is (= {:pet pet} (json/decode (:body response) keyword))))

    ;; edn
    (let [response (http/post (url-for ::create-pet) {:body (pr-str pet)
                                                      :headers {"Content-Type" "application/edn"
                                                                "Accept" "application/edn"}})]
      (is (= 201 (:status response)))
      (is (= {:pet pet} (edn/read-string (:body response)))))

    ;; transit+json
    (let [response (http/post (url-for ::create-pet) {:body (transit-write-bytes pet :json)
                                                      :headers {"Content-Type" "application/transit+json"
                                                                "Accept" "application/transit+json"}})]
      (is (= 201 (:status response)))
      (is (= {:pet pet} (transit-read-bytes (.getBytes (:body response)) :json))))

    ;; transit+msgpack
    (let [response (http/post (url-for ::create-pet) {:body (transit-write-bytes pet :msgpack)
                                                      :headers {"Content-Type" "application/transit+msgpack"
                                                                "Accept" "application/transit+msgpack"}
                                                      :as :byte-array})]
      (is (= 201 (:status response)))
      (is (= {:pet pet} (transit-read-bytes (:body response) :msgpack))))))

(deftest helpers-test
  (let [response (http/get (url-for ::get-pet-by-name :path-params {:name "Keiran"}))]
    (is (= 200 (:status response)))
    (is (= {:name "Keiran"
            :type "dog"
            :age 6}
           (json/decode (:body response) keyword)))))

(deftest optional-parameters-test
  (let [response (http/get (url-for ::get-all-pets) {:headers {"Content-Type" "application/json"}})]
    (is (= 200 (:status response))))

  (let [response (http/get (url-for ::get-all-pets :query-params {:sort "asc"})
                           {:headers {"Content-Type" "application/json"}})]
    (is (= 200 (:status response)))))

(deftest schema-errors-test
  (let [response (http/post (url-for ::create-pet) {:body (json/encode {:name "Bob" :type "dog" :age "abc"})
                                                    :headers {"Content-Type" "application/json"}
                                                    :throw-exceptions false})]
    (is (= 400 (:status response)))
    (is (= "{:error {:body-params {:age \"(not (integer? abc))\"}}}"
           (:body response)))))

(deftest swagger-schema-test
  (let [response (http/get (url-for ::route-swagger.doc/swagger-json) {:as :json})]
    (is (= 200 (:status response)))
    (is (:body response))))

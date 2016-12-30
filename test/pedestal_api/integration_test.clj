(ns pedestal-api.integration-test
  (:require [pedestal-api
             [core :as api]
             [helpers :refer [defhandler]]
             [test-fixture :as tf]]
            [io.pedestal.interceptor :refer [interceptor]]
            [schema.core :as s]
            [clj-http.client :as http]
            [clojure.test :refer :all]
            [cheshire.core :as json]
            [clojure.edn :as edn]))

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

(defhandler events
  {:parameters {:path-params {:topic (s/constrained s/Str #(re-find #"/" %))}}
   :responses {200 {:body s/Str}}}
  [{:keys [path-params]}]
  {:status 200
   :body (:topic path-params)})

(s/with-fn-validation
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
       ["/events/*topic" {:get events}]
       ["/swagger.json" {:get api/swagger-json}]]]]))

(use-fixtures :once (tf/with-server #(deref #'routes)))

(def ^:private url-for (partial tf/url-for routes))

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
           (tf/transit-read-bytes (.getBytes (:body response)) :json))))

  (let [response (http/get (url-for ::get-all-pets) {:headers {"Accept" "application/transit+msgpack"}
                                                     :as :byte-array})]
    (is (= 200 (:status response)))
    (is (= "application/transit+msgpack;charset=UTF-8" (get-in response [:headers "Content-Type"])))
    (is (= [{:name "Alfred"
             :type "dog"
             :age 6}]
           (tf/transit-read-bytes (:body response) :msgpack)))))

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
    (let [response (http/post (url-for ::create-pet) {:body (tf/transit-write-bytes pet :json)
                                                      :headers {"Content-Type" "application/transit+json"
                                                                "Accept" "application/transit+json"}})]
      (is (= 201 (:status response)))
      (is (= {:pet pet} (tf/transit-read-bytes (.getBytes (:body response)) :json))))

    ;; transit+msgpack
    (let [response (http/post (url-for ::create-pet) {:body (tf/transit-write-bytes pet :msgpack)
                                                      :headers {"Content-Type" "application/transit+msgpack"
                                                                "Accept" "application/transit+msgpack"}
                                                      :as :byte-array})]
      (is (= 201 (:status response)))
      (is (= {:pet pet} (tf/transit-read-bytes (:body response) :msgpack))))))

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

(deftest splat-params-test
  (let [response (http/get (url-for ::events :path-params {:topic "foo/bar"}))]
    (is (= 200 (:status response)))
    (is (= "foo/bar" (:body response)))))

(deftest swagger-schema-test
  (let [response (http/get (url-for ::route-swagger.doc/swagger-json) {:as :json})]
    (is (= 200 (:status response)))
    (is (:body response))
    (is (get-in response [:body :paths (keyword "/events/{topic}")]))
    (is (= "events" (get-in response [:body :paths (keyword "/events/{topic}") :get :operationId])))))

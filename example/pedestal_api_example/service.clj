(ns pedestal-api-example.service
  (:require [pedestal-api
             [swagger :as swagger]
             [request-params :refer [body-params common-body]]
             [content-negotiation :refer [negotiate-response]]
             [error-handling :refer [error-responses]]
             [routes :refer [defroutes]]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.interceptor :refer [interceptor]]
            [schema.core :as s]))

(s/defschema Pet
  {:name s/Str
   :type s/Str
   :age s/Int})

(def create-pet
  (swagger/annotate
   {:summary   "Create a pet"
    :parameters {:body-params Pet}
    :responses {201 {:body {:id s/Int
                            :pet Pet}}}}
   (interceptor
    {:name  ::create-pet
     :enter (fn [ctx]
              (assoc ctx :response
                     {:status 201
                      :body {:id 1
                             :pet (get-in ctx [:request :body-params])}}))})))

(def get-all-pets
  (swagger/annotate
   {:summary   "Get all pets in the store"
    :responses {200 {:body {:total s/Int}}}}
   (interceptor
    {:name  ::get-all-pets
     :enter (fn [ctx]
              (assoc ctx :response
                     {:status 200
                      :body {:total 0}}))})))

(defroutes routes
  {:info {:title       "Swagger Sample App"
          :description "This is a sample Petstore server."
          :version     "2.0"}
   :tags [{:name         "pets"
           :description  "Everything about your Pets"
           :externalDocs {:description "Find out more"
                          :url         "http://swagger.io"}}
          {:name        "orders"
           :description "Operations about orders"}]}
  [[["/" ^:interceptors [error-responses
                         (negotiate-response)
                         (body-params)
                         common-body
                         (swagger/coerce-request)
                         (swagger/validate-response)]
     ["/pets" ^:interceptors [(swagger/doc {:tags ["pets"]})]
      {:get get-all-pets
       :post create-pet}]

     ["/swagger.json" {:get swagger-json}]
     ["/*resource" {:get swagger-ui}]]]])

(def service
  {:env                      :dev
   ::bootstrap/routes        #(deref #'routes)
   ::bootstrap/router        :linear-search
   ::bootstrap/resource-path "/public"
   ::bootstrap/type          :jetty
   ::bootstrap/port          8080
   ::bootstrap/join?         false})

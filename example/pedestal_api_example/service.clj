(ns pedestal-api-example.service
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [pedestal-api.core :as api]
            [schema.core :as s]))

(s/defschema Pet
  {:name s/Str
   :type s/Str
   :age s/Int})

(def create-pet
  (api/annotate
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
  (api/annotate
   {:summary   "Get all pets in the store"
    :responses {200 {:body {:total s/Int}}}}
   (interceptor
    {:name  ::get-all-pets
     :enter (fn [ctx]
              (assoc ctx :response
                     {:status 200
                      :body {:total 0}}))})))

(api/defroutes routes
  {:info {:title       "Swagger Sample App"
          :description "This is a sample Petstore server."
          :version     "2.0"}
   :tags [{:name         "pets"
           :description  "Everything about your Pets"
           :externalDocs {:description "Find out more"
                          :url         "http://swagger.io"}}
          {:name        "orders"
           :description "Operations about orders"}]}
  [[["/" ^:interceptors [api/error-responses
                         (api/negotiate-response)
                         (api/body-params)
                         api/common-body
                         (api/coerce-request)
                         (api/validate-response)]
     ["/pets" ^:interceptors [(api/doc {:tags ["pets"]})]
      {:get get-all-pets
       :post create-pet}]

     ["/swagger.json" {:get api/swagger-json}]
     ["/*resource" {:get api/swagger-ui}]]]])

(def service
  {:env                      :dev
   ::bootstrap/routes        #(deref #'routes)
   ::bootstrap/router        :linear-search
   ::bootstrap/resource-path "/public"
   ::bootstrap/type          :jetty
   ::bootstrap/port          8080
   ::bootstrap/join?         false})

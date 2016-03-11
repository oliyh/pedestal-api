(ns pedestal-api-example.service
  (:require [io.pedestal.http :as bootstrap]
            [io.pedestal.interceptor :refer [interceptor]]
            [pedestal-api
             [core :as api]
             [helpers :refer [handler defhandler]]]
            [schema.core :as s])
  (:import [java.util UUID]))

(defonce the-pets (atom {}))

(s/defschema Pet
  {:name s/Str
   :type s/Str
   :age s/Int})

(s/defschema PetWithId
  (assoc Pet (s/optional-key :id) s/Uuid))

(def all-pets
  "Example of annotating a generic interceptor"
  (api/annotate
   {:summary    "Get all pets in the store"
    :parameters {}
    :responses  {200 {:body {:pets [PetWithId]}}}}
   (interceptor
    {:name  ::all-pets
     :enter (fn [ctx]
              (assoc ctx :response
                     {:status 200
                      :body {:pets (vals @the-pets)}}))})))

(def create-pet
  "Example of using the handler helper"
  (handler
   ::create-pet
   {:summary    "Create a pet"
    :parameters {:body-params Pet}
    :responses  {201 {:body {:id s/Uuid}}}}
   (fn [request]
     (let [id (UUID/randomUUID)]
       (swap! the-pets assoc id (assoc (:body-params request) :id id))
       {:status 201
        :body {:id id}}))))

;; Example of using the defhandler helper
(defhandler load-pet
  {:summary    "Load a pet by id"
   :parameters {:path-params {:id s/Uuid}}
   :responses  {200 {:body PetWithId}
                404 {:body s/Str}}}
  [{:keys [path-params] :as request}]
  (if-let [pet (get @the-pets (:id path-params))]
    {:status 200
     :body pet}
    {:status 404
     :body "No pet found with that id"}))

(s/with-fn-validation
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
        ["/" {:get all-pets
              :post create-pet}]
        ["/:id" {:get load-pet}]]

       ["/swagger.json" {:get api/swagger-json}]
       ["/*resource" {:get api/swagger-ui}]]]]))

(def service
  {:env                      :dev
   ::bootstrap/routes        #(deref #'routes)
   ::bootstrap/router        :linear-search
   ::bootstrap/resource-path "/public"
   ::bootstrap/type          :jetty
   ::bootstrap/port          8080
   ::bootstrap/join?         false})

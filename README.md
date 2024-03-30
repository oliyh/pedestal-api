# pedestal-api
A batteries-included API for Pedestal using Swagger.

**pedestal-api** is a library for building APIs on the pedestal web server.
It implements the parts of HTTP that are useful for APIs and allows you to document your handlers and middleware
using idiomatic Clojure and generate a compliant Swagger specification.

[![Clojars Project](https://img.shields.io/clojars/v/pedestal-api.svg)](https://clojars.org/pedestal-api)

The [example code](https://github.com/oliyh/pedestal-api/tree/master/example) can be seen at https://pedestal-api.oliy.co.uk

## Features

* A [Swagger](http://swagger.io) API with input and output validation and coercion as provided by [route-swagger](https://github.com/frankiesardo/route-swagger).
* Content deserialisation, including:
  * `application/json`
  * `application/edn`
  * `application/transit+json`
  * `application/transit+msgpack`
  * `application/x-www-form-urlencoded`
* Content negotiation, including:
  * `application/json`
  * `application/edn`
  * `application/transit+json`
  * `application/transit+msgpack`
* Human-friendly error messages when schema validation fails
  * e.g. `{:error {:body-params {:age "(not (integer? abc))"}}}`
* Convenience functions for annotating routes and interceptors

## Flexibility

pedestal-api is built on top of [route-swagger](https://github.com/frankiesardo/route-swagger) which can still
be used directly if more flexibility is needed. Interceptors are provided but not wired in, allowing you to choose
those which suit you best.

## Example

The [example code](https://github.com/oliyh/pedestal-api/tree/master/example) (reproduced below)
can be seen running on Heroku at https://pedestal-api.herokuapp.com

```clojure
(ns pedestal-api-example.service
  (:require [io.pedestal.http :as bootstrap]
            [io.pedestal.interceptor.chain :refer [terminate]]
            [io.pedestal.interceptor :refer [interceptor]]
            [pedestal-api
             [core :as api]
             [helpers :refer [before defbefore defhandler handler]]]
            [schema.core :as s])
  (:import java.util.UUID))

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
   {:summary     "Get all pets in the store"
    :parameters  {:query-params {(s/optional-key :sort) (s/enum :asc :desc)}}
    :responses   {200 {:body {:pets [PetWithId]}}}
    :operationId :all-pets}
   (interceptor
    {:name  ::all-pets
     :enter (fn [ctx]
              (assoc ctx :response
                     {:status 200
                      :body {:pets (let [sort (get-in ctx [:request :query-params :sort])]
                                     (cond->> (vals @the-pets)
                                       sort (sort-by :name)
                                       (= :desc sort) reverse))}}))})))

(def create-pet
  "Example of using the handler helper"
  (handler
   ::create-pet
   {:summary     "Create a pet"
    :parameters  {:body-params Pet}
    :responses   {201 {:body {:id s/Uuid}}}
    :operationId :create-pet}
   (fn [request]
     (let [id (UUID/randomUUID)]
       (swap! the-pets assoc id (assoc (:body-params request) :id id))
       {:status 201
        :body {:id id}}))))

;; Example using the defbefore helper
(defbefore load-pet
  {:summary    "Load a pet by id"
   :parameters {:path-params {:id s/Uuid}}
   :responses  {404 {:body s/Str}}}
  [{:keys [request] :as context}]
  (if-let [pet (get @the-pets (get-in request [:path-params :id]))]
    (update context :request assoc :pet pet)
    (-> context terminate (assoc :response {:status 404
                                            :body "No pet found with this id"}))))

;; Example of using the defhandler helper
(defhandler get-pet
  {:summary     "Get a pet by id"
   :parameters  {:path-params {:id s/Uuid}}
   :responses   {200 {:body PetWithId}
                 404 {:body s/Str}}
   :operationId :get-pet}
  [{:keys [pet] :as request}]
  {:status 200
   :body pet})

(def update-pet
  "Example of using the before helper"
  (before
   ::update-pet
   {:summary     "Update a pet"
    :parameters  {:path-params {:id s/Uuid}
                  :body-params Pet}
    :responses   {200 {:body s/Str}}
    :operationId :update-pet}
   (fn [{:keys [request]}]
     (swap! the-pets update (get-in request [:path-params :id]) merge (:body-params request))
     {:status 200
      :body "Pet updated"})))

(def delete-pet
  "Example of annotating a generic interceptor"
  (api/annotate
   {:summary     "Delete a pet by id"
    :parameters  {:path-params {:id s/Uuid}}
    :responses   {200 {:body s/Str}}
    :operationId :delete-pet}
   (interceptor
    {:name  ::delete-pet
     :enter (fn [ctx]
              (let [pet (get-in ctx [:request :pet])]
                (swap! the-pets dissoc (:id pet))
                (assoc ctx :response
                       {:status 200
                        :body (str "Deleted " (:name pet))})))})))

(s/with-fn-validation
  (api/defroutes routes
    {:info {:title       "Swagger Sample App built using pedestal-api"
            :description "Find out more at https://github.com/oliyh/pedestal-api"
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
        ["/:id" ^:interceptors [load-pet]
         {:get get-pet
          :put update-pet
          :delete delete-pet}]]

       ["/swagger.json" {:get api/swagger-json}]
       ["/*resource" {:get api/swagger-ui}]]]]))

(def service
  {:env                      :dev
   ::bootstrap/routes        #(deref #'routes)
   ;; linear-search, and declaring the swagger-ui handler last in the routes,
   ;; is important to avoid the splat param for the UI matching API routes
   ::bootstrap/router        :linear-search
   ::bootstrap/resource-path "/public"
   ::bootstrap/type          :jetty
   ::bootstrap/port          8080
   ::bootstrap/join?         false})
```

## Build
[![Circle CI](https://circleci.com/gh/oliyh/pedestal-api.svg?style=svg)](https://circleci.com/gh/oliyh/pedestal-api)

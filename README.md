# pedestal-api
A batteries-included API for Pedestal using Swagger

## Download
[![Clojars Project](https://img.shields.io/clojars/v/pedestal-api.svg)](https://clojars.org/pedestal-api)

Note that you must provide pedestal within your own project's dependencies.

## Swagger
A [Swagger](http://swagger.io) API with input and output validation and coercion as provided by [route-swagger](https://github.com/frankiesardo/route-swagger).

## Batteries
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
be used directly if more flexibility is needed.

## Build
[![Circle CI](https://circleci.com/gh/oliyh/pedestal-api.svg?style=svg)](https://circleci.com/gh/oliyh/pedestal-api)

## Example

See the [example code](https://github.com/oliyh/pedestal-api/tree/master/example) or below:

```clojure
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
```

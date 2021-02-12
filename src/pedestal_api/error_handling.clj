(ns pedestal-api.error-handling
  (:require [route-swagger.interceptor :as sw.int]
            [route-swagger.doc :as sw.doc]
            [ring.swagger.middleware :refer [stringify-error]]
            [io.pedestal.interceptor.error :as error]
            [ring.util.http-status :as status]
            [io.pedestal.http.body-params :as body-params]))

(def error-responses
  (sw.doc/annotate
    {:responses {status/bad-request {}
                 status/internal-server-error {}}}
    (error/error-dispatch [ctx ex]
      [{:interceptor ::body-params/body-params}]
      (assoc ctx :response {:status status/bad-request :body "Cannot deserialise body" :headers {"Content-Type" "text/plain"}})

      [{:interceptor ::sw.int/coerce-request}]
      (assoc ctx :response {:status status/bad-request :body (stringify-error (:error (ex-data ex))) :headers {"Content-Type" "text/plain"}})

      [{:interceptor ::sw.int/validate-response}]
      (assoc ctx :response {:status status/internal-server-error :body (stringify-error (:error (ex-data ex))) :headers {"Content-Type" "text/plain"}}))))

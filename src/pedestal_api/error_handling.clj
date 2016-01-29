(ns pedestal-api.error-handling
  (:require [route-swagger.interceptor :as sw.int]
            [route-swagger.doc :as sw.doc]
            [ring.swagger.middleware :refer [stringify-error]]
            [io.pedestal.interceptor.error :as error]
            [ring.util.http-status :as status] ;; todo use these?
            ))

(def error-responses
  (sw.doc/annotate
    {:responses {400 {}
                 500 {}}}
    (error/error-dispatch [ctx ex]
      [{:interceptor ::sw.int/coerce-request}]
      (assoc ctx :response {:status 400 :body (stringify-error (:error (ex-data ex)))})

      [{:interceptor ::sw.int/validate-response}]
      (assoc ctx :response {:status 500 :body (stringify-error (:error (ex-data ex)))}))))

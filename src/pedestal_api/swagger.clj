(ns pedestal-api.swagger
  (:require [route-swagger.interceptor :as sw.int]
            [route-swagger.doc :as sw.doc]
            [io.pedestal.interceptor :as i]
            [potemkin :refer [import-vars]]))

(import-vars [route-swagger.interceptor coerce-request validate-response]
             [route-swagger.doc annotate])

(def swagger-json (i/interceptor (sw.int/swagger-json)))

(defn make-swagger-ui [& opts]
  (i/interceptor
   (apply sw.int/swagger-ui opts)))

(def swagger-ui (make-swagger-ui))

(defn doc
  "Adds metatata m to a swagger route"
  [m]
  (sw.doc/annotate
    m
    (i/interceptor
      {:name  ::doc
       :enter identity})))

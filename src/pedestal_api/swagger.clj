(ns pedestal-api.swagger
  (:require [route-swagger.interceptor :as sw.int]
            [route-swagger.doc :as sw.doc]
            [io.pedestal.interceptor :as i]
            [potemkin :refer [import-vars]]))

(import-vars [route-swagger.interceptor coerce-request validate-response]
             [route-swagger.doc annotate])

(def swagger-json (i/interceptor (sw.int/swagger-json)))

(defn- realise-url-for [ctx]
  (update-in ctx [:request :url-for] deref))

(defn- delay-url-for [ctx]
  (update-in ctx [:request :url-for] #(delay %)))

(defn- safe-comp [& fs]
  (apply comp (remove nil? fs)))

(defn make-swagger-ui [& opts]
  (i/interceptor
   (-> (apply sw.int/swagger-ui opts)
       (update :enter safe-comp realise-url-for)
       (update :leave safe-comp delay-url-for))))

(def swagger-ui (make-swagger-ui))

(defn doc
  "Adds metatata m to a swagger route"
  [m]
  (sw.doc/annotate
    m
    (i/interceptor
      {:name  ::doc
       :enter identity})))

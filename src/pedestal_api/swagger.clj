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

(def swagger-ui
  (i/interceptor
   (-> (sw.int/swagger-ui)
       (update :enter comp realise-url-for)
       (update :leave comp delay-url-for))))

(defn doc
  "Adds metatata m to a swagger route"
  [m]
  (sw.doc/annotate
    m
    (i/interceptor
      {:name  ::doc
       :enter identity})))

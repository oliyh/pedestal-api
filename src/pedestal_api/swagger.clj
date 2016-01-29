(ns pedestal-api.swagger
  (:require [route-swagger.interceptor :as sw.int]
            [route-swagger.doc :as sw.doc]
            [io.pedestal.interceptor :as i]))

(def swagger-json (i/interceptor (sw.int/swagger-json)))

(def swagger-ui (i/interceptor {:name  :foo
                                :enter (fn [context]
                                         ((:enter (sw.int/swagger-ui)) context))}))

(defn doc
  "Adds metatata m to a swagger route"
  [m]
  (sw.doc/annotate
    m
    (i/interceptor
      {:name  ::doc
       :enter identity})))

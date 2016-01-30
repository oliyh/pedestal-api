(ns pedestal-api.routes
  (:require [route-swagger.doc :as sw.doc]
            [io.pedestal.http.route.definition :as definition]))

(defmacro defroutes [n doc routes]
  `(def ~n (-> ~routes definition/expand-routes (sw.doc/with-swagger ~doc))))

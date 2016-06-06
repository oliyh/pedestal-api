(ns pedestal-api.routes
  (:require [route-swagger.doc :as sw.doc]
            [io.pedestal.http.route :as route]))

(defmacro defroutes [n doc routes]
  `(def ~n (-> ~routes route/expand-routes (sw.doc/with-swagger ~doc))))

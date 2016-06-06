(ns pedestal-api.routes
  (:require [route-swagger.doc :as sw.doc]
            [io.pedestal.http.route :as route]
            [clojure.string :as string]))

(defn replace-splat-parameters [route-table]
  (map (fn [route]
         (update route :path #(string/replace % "*" ":")))
       route-table))

(defmacro defroutes [n doc routes]
  `(def ~n (-> ~routes route/expand-routes replace-splat-parameters (sw.doc/with-swagger ~doc))))

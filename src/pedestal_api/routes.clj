(ns pedestal-api.routes
  (:require [route-swagger.doc :as sw.doc]
            [io.pedestal.http.route :as route]
            [clojure.string :as string]))

(defn replace-splat-parameters [route-table]
  (map (fn [route]
         (update route :path #(string/replace % "*" ":")))
       route-table))

(defn default-operation-ids [route-table]
  (map (fn [route]
         (update route :interceptors
                 (fn [interceptors]
                   (if (-> interceptors last meta ::sw.doc/doc)
                     (conj (vec (drop-last interceptors))
                           (vary-meta (last interceptors) update-in [::sw.doc/doc :operationId]
                                      #(or % (name (:route-name route)))))
                     interceptors))))
       route-table))

(defmacro defroutes [n doc routes]
  `(def ~n (-> ~routes
               route/expand-routes
               replace-splat-parameters
               default-operation-ids
               (sw.doc/with-swagger ~doc))))

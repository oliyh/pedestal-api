(ns pedestal-api.routes
  (:require [route-swagger.doc :as sw.doc]
            [io.pedestal.http.route :as route]
            [clojure.string :as string]))

(defn replace-splat-parameters [route-table]
  (map (fn [route]
         (update route :path #(string/replace % "*" ":")))
       route-table))

(defn update-handler-swagger [route-table f]
  (map (fn [route]
         (update route :interceptors
                 (fn [interceptors]
                   (if (-> interceptors last meta ::sw.doc/doc)
                     (conj (vec (drop-last interceptors))
                           (vary-meta (last interceptors) update ::sw.doc/doc (partial f route)))
                     interceptors))))
       route-table))

(defn default-operation-ids [route handler-swagger]
  (merge {:operationId (name (:route-name route))} handler-swagger))

(defn default-empty-parameters [route handler-swagger]
  (merge {:parameters {}} handler-swagger))

(defn comp->>
  "Like comp but multiple arguments are passed to all functions like partial, except the last which is threaded like ->>"
  [f & fs]
  (fn [& args]
    (reduce (fn [r f']
              (apply f' (conj (into [] (butlast args)) r)))
            (apply f args)
            fs)))

(defmacro defroutes [n doc routes]
  `(def ~n (-> ~routes
               route/expand-routes
               replace-splat-parameters
               (update-handler-swagger (comp->> default-operation-ids
                                                default-empty-parameters))
               (sw.doc/with-swagger (merge {:basePath ""} ~doc)))))

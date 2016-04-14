(ns pedestal-api.request-params
  (:require [io.pedestal.http.body-params :as pedestal]
            [io.pedestal.interceptor :as i]
            [route-swagger.doc :as sw.doc]
            [ring.middleware.multipart-params :as multipart-params]
            [clojure.walk :as walk]
            [clojure.set :as set]
            [linked.core :as linked]))

(defn- merge-empty-params [request]
  (merge-with merge
              request
              {:body-params {}
               :form-params {}
               :query-params {}
               :path-params {}
               :headers {}}))

(defn- normalise-params [request]
  (-> request
      (set/rename-keys {:edn-params       :body-params
                        :json-params      :body-params
                        :transit-params   :body-params
                        :multipart-params :form-params})
      (update :form-params walk/keywordize-keys)
      merge-empty-params))

(def common-body
  (i/interceptor
   {:name  ::common-body
    :enter (fn [context]
             (update context :request normalise-params))}))

(defn body-params [& args]
  (sw.doc/annotate
   {:consumes ["application/json"
               "application/edn"
               "application/x-www-form-urlencoded"
               "application/transit+json"
               "application/transit+msgpack"]}
   (apply pedestal/body-params args)))

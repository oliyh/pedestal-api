(ns pedestal-api.request-coercion
  (:require [io.pedestal.http.body-params :as pedestal]
            [io.pedestal.interceptor :as i]
            [route-swagger.doc :as sw.doc]
            [ring.middleware.multipart-params :as multipart-params]
            [clojure.walk :as walk]
            [clojure.set :as set]
            [linked.core :as linked]))

(defn transit-msgpack-parser [request]
  (->> ((pedestal/custom-transit-parser :msgpack) request)
       :transit-params
       (assoc request :body-params)))

(defn transit-json-parser [request]
  (->> ((pedestal/custom-transit-parser :json) request)
       :transit-params
       (assoc request :body-params)))

(defn json-parser [request]
  (->> (pedestal/json-parser request)
       :json-params
       (assoc request :body-params)))

(defn edn-parser [request]
  (->> (pedestal/edn-parser request)
       :edn-params
       (assoc request :body-params)))

(defn urlencoded-parser [request]
  (->> (pedestal/form-parser request)
       :form-params
       walk/keywordize-keys
       (assoc request :form-params)))

(defn multipart-parser [request]
  (->> (multipart-params/multipart-params-request request)
       :multipart-params
       walk/keywordize-keys
       (assoc request :form-params)))

(def default-parser-map
  (linked/map
   "application/json" json-parser
   "application/edn" edn-parser
   "application/transit+json" transit-json-parser
   "application/transit+msgpack" transit-msgpack-parser
   "application/x-www-form-urlencoded" urlencoded-parser
   "multipart/form-data" multipart-parser))

(defn parse-content-type [parser-map request]
  (let [{:keys [content-type] :or {content-type ""}} request
        type (second (re-find #"^(.*?)(?:;|$)" content-type))
        parser (get parser-map type identity)]
    (parser request)))

(def common-body
  (i/interceptor
    {:name  ::common-body
     :enter (fn [context]
              (update context
                      :request
                      set/rename-keys
                      {:edn-params       :body-params
                       :json-params      :body-params
                       :transit-params   :body-params
                       :multipart-params :form-params}))}))

(defn body-params
  "An almost drop-in replacement for pedestal's body-params.
  Accepts a parser map with content-type strings as keys instead of regexes.
  Ensures the body keys assoc'd into the request are the ones coerce-request
  expects and keywordizes keys by default."
  ([] (body-params default-parser-map))
  ([parser-map]
   (sw.doc/annotate
    {:consumes (keys parser-map)}
    (i/interceptor
     {:name ::body-params
      :enter (fn [{:keys [request] :as context}]
               (assoc context :request (parse-content-type parser-map request)))}))))

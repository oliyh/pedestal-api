(ns pedestal-api.content-negotiation
  (:require [clojure.string :as string]
            [io.pedestal.http :as service]
            [io.pedestal.interceptor.chain :refer [terminate enqueue*]]
            [io.pedestal.interceptor.helpers :as interceptor]
            [io.pedestal.http.content-negotiation :as pcn]
            [linked.core :as linked]
            [route-swagger.doc :as sw.doc]
            [ring.util.response :as ring-response]))

(def edn-body
  (interceptor/on-response
   ::edn-body
   (fn [response]
     (let [body (:body response)
           content-type (get-in response [:headers "Content-Type"])]
       (if (and (coll? body) (not content-type))
         (-> response
             (ring-response/content-type "application/edn;charset=UTF-8")
             (assoc :body (:body (service/edn-response body))))
         response)))))

(def default-serialisation-interceptors
  (linked/map
   "application/json" service/json-body
   "application/edn" edn-body
   "application/transit+json" service/transit-json-body
   "application/transit+msgpack" service/transit-msgpack-body
   "application/transit" service/transit-body))

(defn- find-interceptor [serialisation-interceptors accept]
  (get serialisation-interceptors accept))

(defn default-to [content-type]
  (fn [ctx]
    (println "defaulting!")
    (assoc-in ctx [:request :accept] content-type)))

(defn negotiate-response
  ([] (negotiate-response default-serialisation-interceptors))
  ([serialisation-interceptors] (negotiate-response serialisation-interceptors service/json-body))
  ([serialisation-interceptors default-serialiser]
   (let [delegate (pcn/negotiate-content (keys serialisation-interceptors) {:no-match-fn identity})]
     (sw.doc/annotate
      {:produces (keys serialisation-interceptors)
       ;; :responses {406 {}} see comment below
       }
      (interceptor/around
       ::serialise-response

       (fn [ctx] ((:enter delegate) ctx))

       (fn [{:keys [request] :as ctx}]
         (if-let [i (or (find-interceptor serialisation-interceptors (get-in request [:accept :field]))
                        default-serialiser)]
           ((:leave i) ctx)
           ctx)))))))

;; turned off until can work out what to do with things like text/plain,text/html,image/jpg etc
#_(fn [{:keys [request] :as ctx}]
         (let [accept (get-in request [:headers "accept"])]
           (if-not (find-interceptor serialisation-interceptors accept)
             (-> ctx
                 terminate
                 (assoc :response {:status 406
                                   :headers {}
                                   :body (format "No serialiser could be found to generate '%s'."
                                                 accept)}))
             ctx)))

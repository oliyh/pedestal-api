(ns pedestal-api-example.service
  (:require [clojure.core.async :as a]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.impl.interceptor :refer [terminate]]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.http.sse :as sse]
            [pedestal-api
             [core :as api]
             [helpers :refer [before defbefore defhandler handler]]]
            [schema.core :as s])
  (:import java.util.UUID))

(defonce the-pets (atom {}))

(s/defschema Pet
  {:name s/Str
   :type s/Str
   :age s/Int})

(s/defschema PetWithId
  (assoc Pet (s/optional-key :id) s/Uuid))

(def all-pets
  "Example of annotating a generic interceptor"
  (api/annotate
   {:summary    "Get all pets in the store"
    :parameters {}
    :responses  {200 {:body {:pets [PetWithId]}}}}
   (interceptor
    {:name  ::all-pets
     :enter (fn [ctx]
              (assoc ctx :response
                     {:status 200
                      :body {:pets (vals @the-pets)}}))})))

(def create-pet
  "Example of using the handler helper"
  (handler
   ::create-pet
   {:summary    "Create a pet"
    :parameters {:body-params Pet}
    :responses  {201 {:body {:id s/Uuid}}}}
   (fn [request]
     (let [id (UUID/randomUUID)]
       (swap! the-pets assoc id (assoc (:body-params request) :id id))
       {:status 201
        :body {:id id}}))))

;; Example using the defbefore helper
(defbefore load-pet
  {:summary    "Load a pet by id"
   :parameters {:path-params {:id s/Uuid}}
   :responses  {404 {:body s/Str}}}
  [{:keys [request] :as context}]
  (if-let [pet (get @the-pets (get-in request [:path-params :id]))]
    (update context :request assoc :pet pet)
    (-> context terminate (assoc :response {:status 404
                                            :body "No pet found with this id"}))))

;; Example of using the defhandler helper
(defhandler get-pet
  {:summary    "Get a pet by id"
   :parameters {:path-params {:id s/Uuid}}
   :responses  {200 {:body PetWithId}
                404 {:body s/Str}}}
  [{:keys [pet] :as request}]
  {:status 200
   :body pet})

(def update-pet
  "Example of using the before helper"
  (before
   ::update-pet
   {:summary    "Update a pet"
    :parameters {:path-params {:id s/Uuid}
                 :body-params Pet}
    :responses  {200 {:body s/Str}}}
   (fn [{:keys [request]}]
     (swap! the-pets update (get-in request [:path-params :id]) merge (:body-params request))
     {:status 200
      :body "Pet updated"})))

(def delete-pet
  "Example of annotating a generic interceptor"
  (api/annotate
   {:summary    "Delete a pet by id"
    :parameters {:path-params {:id s/Uuid}}
    :responses  {200 {:body s/Str}}}
   (interceptor
    {:name  ::delete-pet
     :enter (fn [ctx]
              (let [pet (get-in ctx [:request :pet])]
                (swap! the-pets dissoc (:id pet))
                (assoc ctx :response
                       {:status 200
                        :body (str "Deleted " (:name pet))})))})))

(defn- initialise-stream [event-channel context]
  (a/go-loop [sent-count 0]
    (a/>! event-channel {:data {:id (UUID/randomUUID)
                                :event (rand-nth ["Deleted" "Created" "Updated"])}})
    (a/<! (a/timeout 100))
    (if (< sent-count 10)
      (recur (inc sent-count))
      (a/close! event-channel))))

(def pet-events
  (api/annotate
   {:summary "Events relating to pets"
    :description "A Server Side Event stream with events about pets"
    :parameters {}
    :responses {200 {:body {:id s/Uuid
                            :event s/Str}}}}
   (sse/start-event-stream initialise-stream)))

(defn- validate-responser [schema response]
  (def s schema)
  (if (satisfies? clojure.core.async.impl.protocols/Channel (:body response))
    (let [coercer (schema.coerce/coercer (:body schema) {})]
      (def r response)
      (assoc response :body (a/map (fn [event]
                                     (println "Coercing event" (String. event))
                                     (coercer event)) [(:body response)])))
    response))

(s/with-fn-validation
  (api/defroutes routes
    {:info {:title       "Swagger Sample App built using pedestal-api"
            :description "Find out more at https://github.com/oliyh/pedestal-api"
            :version     "2.0"}
     :tags [{:name         "pets"
             :description  "Everything about your Pets"
             :externalDocs {:description "Find out more"
                            :url         "http://swagger.io"}}
            {:name        "orders"
             :description "Operations about orders"}]}
    [[["/" ^:interceptors [api/error-responses
                           (api/negotiate-response)
                           (api/body-params)
                           api/common-body
                           (api/coerce-request)
                           (api/validate-response validate-responser)]
       ["/pets" ^:interceptors [(api/doc {:tags ["pets"]})]
        ["/" {:get all-pets
              :post create-pet}]
        ["/events" {:get pet-events}]
        ["/:id" ^:interceptors [load-pet]
         {:get get-pet
          :put update-pet
          :delete delete-pet}]]

       ["/swagger.json" {:get api/swagger-json}]
       ["/*resource" {:get api/swagger-ui}]]]]))

(def service
  {:env                      :dev
   ::bootstrap/routes        #(deref #'routes)
   ::bootstrap/router        :linear-search
   ::bootstrap/resource-path "/public"
   ::bootstrap/type          :jetty
   ::bootstrap/port          8080
   ::bootstrap/join?         false})

(ns pedestal-api-example.server
  (:require [pedestal-api-example.service :as service]
            [io.pedestal.http :as bootstrap]))

(defonce service-instance nil)

(defn create-server []
  (alter-var-root #'service-instance
                  (constantly (bootstrap/create-server
                               (-> service/service
                                   (bootstrap/default-interceptors))))))

(defn start []
  (when service-instance
    (bootstrap/start service-instance)))

(defn stop []
  (when service-instance
    (bootstrap/stop service-instance)))

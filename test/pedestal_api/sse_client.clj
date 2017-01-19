(ns pedestal-api.sse-client
  (:require [clojure.core.async :as a]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clj-http.client :as http])
  (:import [java.io InputStream]))

;; from https://gist.github.com/oliyh/2b9b9107e7e7e12d4a60e79a19d056ee

(def event-mask (re-pattern (str "(?s).+?\r\n\r\n")))

(defn- parse-event [raw-event]
  (->> (re-seq #"(.*): (.*)\n?" raw-event)
       (map #(drop 1 %))
       (group-by first)
       (reduce (fn [acc [k v]]
                 (assoc acc (keyword k) (string/join (map second v)))) {})))

(defn connect [url & [params]]
  (let [event-stream ^InputStream (:body (http/get url (merge params {:as :stream})))
        events (a/chan (a/sliding-buffer 10) (map parse-event))]
    (a/thread
      (loop [data nil]
        (let [byte-array (make-array Byte/TYPE (max 1 (.available event-stream)))
              bytes-read (.read event-stream byte-array)]

          (if (neg? bytes-read)

            (do (println "Input stream closed, exiting read-loop")
                (.close event-stream))

            (let [data (str data (slurp (io/input-stream byte-array)))]

              (if-let [es (not-empty (re-seq event-mask data))]
                (if (every? true? (map #(a/>!! events %) es))
                  (recur (string/replace data event-mask ""))
                  (do (println "Output stream closed, exiting read-loop")
                      (.close event-stream)))

                (recur data)))))))
    events))

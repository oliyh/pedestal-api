(ns pedestal-api.helpers
  (:require [io.pedestal.interceptor.helpers]
            [pedestal-api.swagger :as swagger]))

(defmacro defhelper [helper-name]
  `(defmacro ~(symbol (str "def" helper-name))
     [name# swagger# & args#]
     `(def ~name#
        (swagger/annotate ~swagger#
                          (@~(ns-resolve 'io.pedestal.interceptor.helpers '~helper-name)
                           (keyword (name (ns-name *ns*)) (name '~name#))
                           (fn ~@args#))))))

;; shadows helper macros in io.pedestal.interceptor.helpers
;; adding swagger metadata as the second argument
;; e.g. (defhandler create-pet
;;                  {:summary "Creates a pet"}
;;                  [request]
;;                  {:status 200
;;                   :body "Created pet"})

;; Note that pedestal recommends building interceptors directly, e.g.
;; (i/interceptor {:name ::my-interceptor
;;                 :before (fn [context] ... )

(defhelper before)
(defhelper after)
(defhelper around)
(defhelper on-request)
(defhelper on-response)
(defhelper handler)
(defhelper middleware)

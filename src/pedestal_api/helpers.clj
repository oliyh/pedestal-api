(ns pedestal-api.helpers
  (:require [io.pedestal.interceptor.helpers]
            [pedestal-api.swagger :as swagger]))

(defmacro defhelper [helper-name]
  `(do (defmacro ~(symbol helper-name)
         [name# swagger# & args#]
         `(def ~name#
            (swagger/annotate ~swagger#
                              (@~(ns-resolve 'io.pedestal.interceptor.helpers '~helper-name)
                               (keyword (name (ns-name *ns*)) (name '~name#))
                               (fn ~@args#)))))

       (defn ~helper-name [name# swagger# & args#]
         (swagger/annotate
          swagger#
          (apply @~(ns-resolve 'io.pedestal.interceptor.helpers helper-name) name# args#)))))

;; shadows helper macros in io.pedestal.interceptor.helpers
;; adding swagger metadata as the second argument, e.g.
;; (defhandler create-pet
;;             {:summary "Creates a pet"}
;;             [request]
;;             {:status 200
;;              :body "Created pet"})
;;
;; also shadows helper functions in io.pedestal.interceptor.helpers,
;; again adding swagger metadata as the second argument, e.g.
;; (handler ::create-pet
;;          {:summary "Creates a pet"}
;;          (fn [request]
;;              {:status 200
;;               :body "Created pet"}))
;;
;; Note that pedestal recommends building interceptors directly,
;; to which you should add swagger metadata, e.g.
;; (swagger/annotate
;;   {:summary "Creates a pet"}
;;   (i/interceptor {:name ::create-pet
;;                   :enter (fn [context] {:status 200
;;                                         :body "Created pet"})}))
;;
;; All these forms create equivalent interceptors.

(defhelper defbefore)
(defhelper defafter)
(defhelper defaround)
(defhelper defon-request)
(defhelper defon-response)
(defhelper defhandler)
(defhelper defmiddleware)

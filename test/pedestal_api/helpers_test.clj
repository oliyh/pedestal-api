(ns pedestal-api.helpers-test
  (:require [pedestal-api.core :as api]
            [pedestal-api.helpers :refer :all]
            [schema.core :as s]
            [io.pedestal.interceptor.helpers :as i]
            [clojure.test :refer :all]))

(defbefore test-defbefore
  {:summary "A test interceptor"
   :parameters {:body-params {:age s/Int}}
   :responses {200 {:body s/Str}}}
  [{:keys [request] :as context}]
  (assoc context :response request))

(deftest defbefore-test
  (is (= ::test-defbefore (:name test-defbefore)))

  (is (= (meta (api/annotate
                {:summary "A test interceptor"
                 :parameters {:body-params {:age s/Int}}
                 :responses {200 {:body s/Str}}}
                (i/before ::test-defbefore
                          (fn [{:keys [request] :as context}]
                            (assoc context :response request)))))
         (meta test-defbefore)))

  (is (= {:request {:a 1}
          :response {:a 1}}

         ((:enter test-defbefore) {:request {:a 1}}))))


(def test-before (before ::test-before
                         {:summary "A test interceptor"
                          :parameters {:body-params {:age s/Int}}
                          :responses {200 {:body s/Str}}}
                         (fn [{:keys [request] :as context}]
                           (assoc context :response request))))

(deftest before-test
  (is (= ::test-before (:name test-before)))

  (is (= (meta (api/annotate
                {:summary "A test interceptor"
                 :parameters {:body-params {:age s/Int}}
                 :responses {200 {:body s/Str}}}
                (i/before ::test-before
                          (fn [{:keys [request] :as context}]
                            (assoc context :response request)))))
         (meta test-before)))

  (is (= {:request {:a 1}
          :response {:a 1}}

         ((:enter test-before) {:request {:a 1}}))))

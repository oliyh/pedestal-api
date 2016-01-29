(ns user
  (:require [clojure.tools.namespace.repl :as repl]))

(defn dev []
  (require 'dev)
  (in-ns 'dev))

(def refresh repl/refresh)
(def clear repl/clear)

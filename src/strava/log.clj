(ns strava.log
  (:require [clojure.core.async :as a]))

(def log-ch (a/chan 100))

(a/go-loop []
  (when-let [v (a/<! log-ch)]
    (apply println v)
    (recur)))

(defn info [& args]
  (a/put! log-ch (into [:info] args)))

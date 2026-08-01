(ns strava.stats
  (:require [strava.cache :as cache]
            [strava.repo :as repo]
            [strava.track :as track]))

(defn get-track-stats [id]
  (cache/through-cache :stats id (fn []
                                   (println "Computing stats for" id)
                                   (-> id
                                       repo/get-track
                                       track/stats
                                       (assoc :id id)))))

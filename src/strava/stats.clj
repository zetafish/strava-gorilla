(ns strava.stats
  (:require [strava.cache :as cache]
            [strava.repo :as repo]
            [strava.track :as track]
            [strava.util :as u]))

(defn get-track-stats [id]
  (cache/through-cache :stats id (fn []
                                   (println "Computing stats for" id)
                                   (-> id
                                       repo/get-track
                                       track/stats
                                       (assoc :id id)))))

(defn weighted-avg [stats k]
  (let [stats (filter k stats)
        numerator (reduce + (map #(* (:sample-count %) (k %)) stats))
        denominator (reduce + (map :sample-count stats))]
    (when (pos? denominator)
      (/ numerator denominator))))

(defn aggregate-activities [activities]
  (let [stats (->> (map :id activities)
                   (map #(get-track-stats %)))
        hr (weighted-avg stats :heart-rate)
        cad (weighted-avg stats :cadence)
        sl (weighted-avg stats :step-length)
        distance (u/sum stats :distance)
        elapsed (u/sum stats :elapsed)
        moving (u/sum stats :moving)
        covered (u/sum stats :covered)
        speed (/ covered moving)]
    (u/with-derived-metrics {:runs (count activities)
                             :distance distance
                             :elapsed elapsed
                             :covered covered
                             :moving moving
                             :heart-rate hr
                             :cadence cad
                             :step-length sl
                             :speed speed})))

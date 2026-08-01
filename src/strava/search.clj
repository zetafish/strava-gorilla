(ns strava.search
  (:require [clojure.string :as str]
            [strava.repo :as repo])
  (:import (java.time
            Instant
            LocalDateTime
            ZoneId)))

;; The FIT Profile defines the date_time type as an uint32 that
;; represents the number of seconds since midnight on December 31,
;; 1989 UTC*. This date is often referred to as the FIT Epoch.
;;
;; To use FIT Epoch values with any of these methods, the value needs
;; to be offset by either 631065600 seconds or 631065600000
;; milliseconds, depending on the method.
(def ^:private fit-epoch-offset 631065600)

(defn fit-ts->local-dt [ts]
  (str (LocalDateTime/ofInstant (Instant/ofEpochSecond (+ ts fit-epoch-offset))
                                (ZoneId/of "Europe/Amsterdam"))))

(defn build-state []
  (let [calendars (repo/load-calendars)
        id->date (reduce (fn [acc m]
                           (assoc acc (:id m) (:date m)))
                         {}
                         calendars)
        activities (->> (repo/load-activities)
                        (map #(assoc % :date (-> % :id id->date)))
                        (filter #(= "Run" (:activity_type %)))
                        (sort-by :date))]
    {:calendars calendars
     :activities activities}))

(defonce state (atom (build-state)))

(defn refresh! []
  (reset! state (build-state))
  nil)

(defn find-activities [{:keys [id pattern from to dist-min dist-max limit]}]
  (cond->> (:activities @state)
    id (filter #(= id (:id %)))
    pattern (filter #(str/includes? (str/lower-case (:title %)) (str/lower-case pattern)))
    from (filter #(<= 0 (compare (:date %) from)))
    to (filter #(<= 0 (compare to (:date %))))
    dist-min (filter #(>= (:distance %) (* 1000 dist-min)))
    dist-max (filter #(<= (:distance %) (* 1000 dist-max)))
    limit (take limit)))

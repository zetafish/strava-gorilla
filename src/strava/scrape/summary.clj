(ns strava.scrape.summary
  "Derive an API-summary-shaped map from a calendar row + activity page + FIT file."
  (:require [strava.parser.core :as parser]
            [strava.scrape.activity :as scrape-act]
            [strava.track :as track]))

(def ^:private fit-epoch-offset 631065600)

(defn- fit-ts->iso [ts]
  (str (java.time.Instant/ofEpochSecond (+ ts fit-epoch-offset))))

(defn- mean [xs]
  (when (seq xs)
    (/ (double (reduce + xs)) (count xs))))

(defn derive-from-fit [fit-path]
  (let [records (-> fit-path parser/parse-original track/trim-head track/trim-tail)]
    (when (seq records)
      (let [first-r (first records)
            last-r (last records)
            hrs (keep :heart_rate records)
            speeds (keep :speed records)
            cadences (keep :cadence records)
            has-hr (boolean (seq hrs))
            start-ts (:timestamp first-r)
            end-ts (:timestamp last-r)
            elapsed (- end-ts start-ts)]
        (cond-> {:distance (:distance last-r)
                 :moving_time elapsed
                 :elapsed_time elapsed
                 :start_date (fit-ts->iso start-ts)
                 :has_heartrate has-hr}
          has-hr        (assoc :average_heartrate (mean hrs)
                               :max_heartrate (apply max hrs))
          (seq speeds)  (assoc :average_speed (mean speeds)
                               :max_speed (apply max speeds))
          (seq cadences) (assoc :average_cadence (mean cadences)))))))

(defn- from-activity-page [page]
  {:name         (:title page)
   :description  (:description page)
   :elev_gain    (:elev_gain page)
   :calories     (:calories page)
   :workout_type (:workout_type page)
   :trainer      (:trainer page)})

(defn merge-summary
  "Combine calendar row + activity-page + FIT-derived fields into a curated
   activity summary. FIT is preferred over page for overlapping fields
   (heartrate, speed, cadence) since it's per-second-accurate."
  [cal-row fit-path]
  (let [page (scrape-act/fetch (:id cal-row))
        fit  (derive-from-fit fit-path)]
    (into {:id (:id cal-row)
           :type (:type cal-row)
           :sport_type (:type cal-row)}
          (remove (comp nil? val))
          (merge (from-activity-page page) fit))))

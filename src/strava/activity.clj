(ns strava.activity
  (:require [strava.cache :as cache]
            [strava.parser.core :as parser]
            [strava.scrape.activity-page :as page]
            [strava.track :as track]))

(def ^:private fit-epoch-offset 631065600)

(defn- fit-ts->iso [ts]
  (str (java.time.Instant/ofEpochSecond (+ ts fit-epoch-offset))))

(defn- mean [xs]
  (when (seq xs)
    (/ (double (reduce + xs)) (count xs))))

(defn- load-records [id fit-path]
  (cache/through-cache
   :tracks id
   #(-> fit-path parser/parse-original track/trim-head track/trim-tail)))

(defn derive-from-fit [id fit-path]
  (let [records (load-records id fit-path)]
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
  (let [id (:id cal-row)
        page (cache/through-cache :pages id #(page/fetch id))
        fit  (derive-from-fit id fit-path)]
    (into {:id id
           :type (:type cal-row)
           :sport_type (:type cal-row)}
          (remove (comp nil? val))
          (merge (from-activity-page page) fit))))

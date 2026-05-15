^nextjournal/clerk
(ns strava.notebook
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [strava.analysis :as analysis]))

;; # Strava FIT File Analysis

;; Browse and visualize your cached FIT file data.

(def cache-dir ".cache")

;; ## Available Activities

(defn activity-id [f]
  (-> f .getName (str/replace #"\.json$" "") parse-long))

(defn load-records [f]
  (json/parse-string (slurp f) true))

(def activities
  (->> (file-seq (io/file cache-dir))
       (filter #(.endsWith (.getName %) ".json"))
       (remove #(.startsWith (.getName %) "."))
       (sort-by activity-id)))

(clerk/table
 {:head ["ID" "Records" "Duration" "Distance (km)" "File"]
  :rows (for [f activities
              :let [recs (load-records f)
                    last-rec (last recs)]]
          [(activity-id f)
           (count recs)
           (when last-rec
             (let [secs (:at last-rec)]
               (format "%d:%02d" (quot secs 60) (mod secs 60))))
           (when last-rec
             (some-> (:distance last-rec) (/ 1000) (format "%.2f")))
           (str/replace #".json$" "" (.getName f))])})

;; ## Activity Detail

;; Pick an activity ID to explore:

(def activity-id-param 6855227463; 10284924043
  )

(defn find-activity-file [id]
  (first (filter #(= (activity-id %) id) activities)))

(def records
  (when-let [f (find-activity-file activity-id-param)]
    (load-records f)))

;; ## Enrichment & Bucketing

;; EF (Efficiency Factor) = speed / heart_rate, bucketized over time windows.

(def bucket-window 60)

(def bucketed
  (when records
    (analysis/bucketize bucket-window records)))

;; ### Efficiency Factor (EF)

;; EF = speed / heart_rate. Higher is better — more distance per heart beat.
;; The metric version (×60) is easier to read.

(when bucketed
  (clerk/vl
   {:data {:values bucketed}
    :mark "line"
    :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
               :y {:field "ef_metric" :type "quantitative" :title "EF (metric)"
                   :scale {:zero false}}
               :color {:value "#e67e22"}}}))

;; ### EF vs Heart Rate

(when bucketed
  (clerk/vl
   {:data {:values bucketed}
    :mark {:type "point" :filled true :opacity 0.6 :size 30}
    :encoding {:x {:field "heart_rate" :type "quantitative" :title "Heart Rate (bpm)"}
               :y {:field "ef_metric" :type "quantitative" :title "EF (metric)"
                   :scale {:zero false}}
               :color {:field "at" :type "quantitative" :scale {:scheme "viridis"}
                       :legend {:title "Time (s)"}}}}))

;; ### EF Pace

(when bucketed
  (clerk/vl
   {:data {:values bucketed}
    :mark "line"
    :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
               :y {:field "pace" :type "quantitative" :title "Pace (s/km)"}
               :color {:value "#1abc9c"}}}))

;; ### Time Series

;; Heart rate, speed, and altitude over the course of the activity.

(when records
  (clerk/vl
   {:data {:values records}
    :layer [{:mark "line"
             :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
                        :y {:field "heart_rate" :type "quantitative" :title "Heart Rate (bpm)"
                            :scale {:zero false}}
                        :color {:value "#e74c3c"}}}
            {:mark "line"
             :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
                        :y {:field "speed" :type "quantitative" :title "Speed (m/s)"
                            :scale {:zero false}}
                        :color {:value "#3498db"}}}
            {:mark "line"
             :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
                        :y {:field "altitude" :type "quantitative" :title "Altitude (m)"
                            :scale {:zero false}}
                        :color {:value "#27ae60"}}}]}))

;; ### Heart Rate

(when records
  (clerk/vl
   {:data {:values records}
    :mark "line"
    :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
               :y {:field "heart_rate" :type "quantitative" :title "Heart Rate (bpm)"}
               :color {:value "#e74c3c"}}}))

;; ### Speed

(when records
  (clerk/vl
   {:data {:values records}
    :mark "line"
    :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
               :y {:field "speed" :type "quantitative" :title "Speed (m/s)"}
               :color {:value "#3498db"}}}))

;; ### Altitude Profile

(when records
  (clerk/vl
   {:data {:values records}
    :mark "line"
    :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
               :y {:field "altitude" :type "quantitative" :title "Altitude (m)"}
               :color {:value "#27ae60"}}}))

;; ### Route Map

(when records
  (clerk/vl
   {:data {:values (keep #(when (and (:position_lat %) (:position_long %))
                            {:lat (:position_lat %)
                             :lng (:position_long %)})
                         records)}
    :mark {:type "line" :stroke "#3498db" :strokeWidth 2}
    :encoding {:latitude {:field "lat" :type "quantitative"}
               :longitude {:field "lng" :type "quantitative"}}}))

;; ### Cadence

(when records
  (clerk/vl
   {:data {:values (keep #(when (:cadence %) %) records)}
    :mark "line"
    :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
               :y {:field "cadence" :type "quantitative" :title "Cadence (rpm)"}
               :color {:value "#9b59b6"}}}))

;; ### Stats Summary

(when bucketed
  (let [hrs (keep :heart_rate bucketed)
        speeds (keep :speed bucketed)
        alts (keep :altitude records)
        cadences (keep :cadence bucketed)
        efs (keep :ef_metric bucketed)]
    (clerk/table
     {:head ["Metric" "Min" "Max" "Avg"]
      :rows [["Heart Rate (bpm)"
              (apply min hrs)
              (apply max hrs)
              (int (double (/ (reduce + hrs) (count hrs))))]
             ["Speed (m/s)"
              (format "%.2f" (apply min speeds))
              (format "%.2f" (apply max speeds))
              (format "%.2f" (double (/ (reduce + speeds) (count speeds))))]
             ["Altitude (m)"
              (format "%.1f" (apply min alts))
              (format "%.1f" (apply max alts))
              (format "%.1f" (double (/ (reduce + alts) (count alts))))]
             ["Cadence (rpm)"
              (apply min cadences)
              (apply max cadences)
              (int (double (/ (reduce + cadences) (count cadences))))]
             ["EF (metric)"
              (format "%.3f" (apply min efs))
              (format "%.3f" (apply max efs))
              (format "%.3f" (double (/ (reduce + efs) (count efs))))]]})))

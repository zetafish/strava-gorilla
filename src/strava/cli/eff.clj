(ns strava.cli.eff
  (:require [babashka.cli :as cli]
            [strava.analysis :as analysis]
            [strava.format :as fmt]
            [strava.repo :as repo]
            [strava.table :as table]
            [strava.track :as track]))

(defn format-point [m]
  (-> m
      (update :at fmt/at->str)
      (update :distance #(some-> % long))
      (update :pace fmt/pace->str)))

(defn print-table [coll]
  (table/print-table
   ["Offset" "HR" "Pace" "km/h" "dist" "EF" "cad" "slen" "pts"]
   [:at :heart_rate :pace :kmph :distance :ef_metric :cadence :step_length :pts]
   coll))

(defn print-summary [summary quarters activity]
  (when activity
    (println (format "  ID:         %s" (:id activity)))
    (println (format "  Name:       %s" (:name activity)))
    (println (format "  Start:      %s" (:start_date_local activity))))
  (println (format "  Distance:   %.1f km" (/ (:distance summary) 1000.0)))
  (println (format "  Duration:   %s" (fmt/at->str (:duration summary))))
  (println (format "  Avg HR:     %s" (:heart_rate summary)))
  (println (format "  Avg Pace:   %s" (:pace summary)))
  (println (format "  Avg EF:     %s" (:ef_metric summary)))
  (let [efs (keep :ef_metric quarters)]
    (when (= 4 (count efs))
      (println (format "  EF Q1-Q4:   %.3f  %.3f  %.3f  %.3f"
                       (nth efs 0) (nth efs 1) (nth efs 2) (nth efs 3)))
      (let [decline (* 100.0 (/ (- (first efs) (last efs)) (first efs)))]
        (println (format "  EF Decline: %.1f%%" decline))))))

(def spec {:pattern {:alias :p :coerce [] :require true}
           :interval {:alias :i :coerce analysis/parse-at :default 3600 :desc "Bucket interval (e.g. 5m, 10s, 1h)"}
           :format {:alias :f :default "table" :validate #{"table" "csv" "json"}}
           :from {:coerce analysis/parse-at :desc "Start time as offset (e.g. 21h15, 3h, 120m, 7200s)"}
           :to {:coerce analysis/parse-at :desc "End time as offset (e.g. 21h15, 3h, 120m, 7200s)"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println (cli/format-opts {:spec spec}))
    true))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})]
        (if-let [activity (first (apply repo/find-by-pattern (:pattern opts)))]
          (let [f (str repo/repo-dir "/" (repo/fit-file-name activity))
                records (cond->> (-> (track/parse-file f) track/add-duration track/remove-head track/remove-tail)
                          (:from opts) (drop-while #(< (:at %) (:from opts)))
                          (:to opts) (take-while #(< (:at %) (:to opts))))
                date (subs (:start_date_local activity) 0 10)
                coll (->> (track/bucketize (:interval opts) records)
                          (map #(assoc % :date date))
                          (map format-point))
                summary (-> (track/agg records) format-point)
                q (quot (count records) 4)
                quarters (mapv #(track/agg %)
                               [(take q records)
                                (->> records (drop q) (take q))
                                (->> records (drop (* 2 q)) (take q))
                                (drop (* 3 q) records)])]
            (print-table coll)
            (print-summary summary quarters activity))
          (println "No fit file found for" (:pattern opts))))))

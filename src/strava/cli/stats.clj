(ns strava.cli.stats
  (:require [babashka.cli :as cli]
            [strava.analysis :as analysis]
            [strava.cli.common :as common]
            [strava.geo :as geo]
            [strava.repo :as repo]
            [strava.table :as table]
            [strava.track :as track]))

(def spec (assoc common/selector-spec
                 :route-sim {:coerce :long :desc "Activity ID for route similarity (adds Score column)"}
                 :threshold {:coerce :long :default 200 :desc "Max route deviation in meters"}
                 :baseline-window {:coerce :long :desc "Baseline EF window in days (adds EF% column)"}
                 :decay {:coerce :double :default 1.0 :desc "Daily decay factor for baseline EF"}))

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb stats [OPTIONS]")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn compute-row [activity]
  (try
    (let [f (str (repo/get-fit-file-by-activity activity))
          records (-> (track/parse-file f) track/add-duration track/remove-head track/remove-tail)
          agg (analysis/agg records)
          drift (analysis/cardiac-drift records)
          split (analysis/positive-split records)]
      {:id (:id activity)
       :date (:start_date activity)
       :name (:name activity)
       :distance (:distance agg)
       :duration (:duration agg)
       :heart_rate (:heart_rate agg)
       :pace (:pace agg)
       :ef (when (and (:average_speed activity) (:average_heartrate activity)
                      (pos? (:average_heartrate activity)))
             (* 60.0 (/ (:average_speed activity) (:average_heartrate activity))))
       :drift drift
       :split split
       :cadence (:cadence agg)})
    (catch Exception _e nil)))

(defn route-filter [activities ref-id threshold]
  (let [ref (repo/find-activity ref-id)
        ref-poly (some-> ref :map :summary_polyline)]
    (if-not ref-poly
      (do (println "Reference activity has no route data:" ref-id)
          [])
      (let [ref-points (geo/decode-polyline ref-poly)
            ref-dist (:distance ref)]
        (->> activities
             (filter #(some-> % :map :summary_polyline))
             (filter #(<= (abs (- 1.0 (/ (:distance % 1) (max 1 ref-dist)))) 0.3))
             (pmap (fn [a]
                     (let [pts (geo/decode-polyline (-> a :map :summary_polyline))]
                       (when (>= (count pts) 2)
                         (assoc a :score (geo/route-similarity ref-points pts))))))
             (filter some?)
             (filter :score)
             (filter #(<= (:score %) threshold)))))))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            activities (repo/find-activities opts)
            activities (if (:route-sim opts)
                         (route-filter activities (:route-sim opts) (:threshold opts))
                         activities)
            rows (->> (keep compute-row activities)
                      (sort-by :date_started))
            show-pct (:baseline-window opts)
            rows (if show-pct
                   (mapv (fn [r]
                           (let [date (some-> (:date r) (subs 0 10))
                                 bl (analysis/baseline-ef @repo/activities
                                                          (:baseline-window opts)
                                                          (:decay opts)
                                                          date)]
                             (assoc r :ef-pct (when (and (:ef r) bl (pos? bl))
                                                (* 100.0 (/ (double (:ef r)) bl))))))
                         rows)
                   rows)
            show-score (:route-sim opts)]
        (if (seq rows)
          (let [header (cond-> ["ID" "Date" "Dist" "Dur" "HR" "Pace" "EF" "Drift" "Split" "Cad"]
                         show-score (conj "Score")
                         show-pct (conj "EF%")
                         true (conj "Name"))
                keys (cond-> [:id :date :distance :duration :heart_rate :pace :ef :drift :split :cadence]
                       show-score (conj :score)
                       show-pct (conj :ef-pct)
                       true (conj :name))]
            (println (format "%d activities" (count rows)))
            (table/print-table header keys rows))
          (println "No activities found")))))

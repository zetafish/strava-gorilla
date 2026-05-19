(ns strava.cli.compare
  (:require [babashka.cli :as cli]
            [strava.analysis :as analysis]
            [strava.cli.common :as common]
            [strava.load :as load]
            [strava.repo :as repo]
            [strava.table :as table]
            [strava.track :as track]))

(def spec (assoc common/selector-spec
                 :baseline-window {:coerce :long :desc "Baseline EF window in days (enables EF% column)"}
                 :decay {:coerce :double :default 1.0 :desc "Daily decay factor for baseline EF"}))

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb compare [OPTIONS]")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn pace->str [seconds]
  (when seconds
    (format "%2d:%02d" (quot seconds 60) (mod seconds 60))))

(defn duration->str [seconds]
  (let [h (quot seconds 3600)
        m (rem (quot seconds 60) 60)]
    (format "%2d:%02d" h m)))

(defn format-row [r]
  (-> r
      (update :distance #(format "%.1fk" %))
      (update :duration #(duration->str (or % 0)))
      (update :avg-hr #(str (or % 0)))
      (update :avg-pace #(or (pace->str %) "-"))
      (update :ef #(if % (format "%.3f" (double %)) "-"))
      (update :drift #(if % (format "%+.1f%%" %) "-"))
      (update :split #(if % (format "%.2f" %) "-"))
      (update :cadence #(str (or % 0)))
      (update :trimp #(if % (format "%.0f" %) "-"))
      (update :name #(let [n (or % "")] (subs n 0 (min (count n) 30))))))

(defn compute-row [activity hr-min hr-max athlete]
  (try
    (let [f (str (repo/get-fit-file-by-activity activity))
          records (-> (track/parse-file f) track/add-duration track/remove-head track/remove-tail)
          agg (analysis/agg records)
          trend (when (and hr-min hr-max) (analysis/trend-summary records hr-min hr-max))
          drift (analysis/cardiac-drift records)
          split (analysis/positive-split records)]
      {:date (some-> (:start_date activity) (subs 0 10))
       :name (:name activity)
       :distance (/ (:distance activity) 1000.0)
       :duration (:duration agg)
       :avg-hr (:heart_rate agg)
       :avg-pace (analysis/pace agg)
       :ef (when (and (:average_speed activity) (:average_heartrate activity)
                      (pos? (:average_heartrate activity)))
             (* 60.0 (/ (:average_speed activity) (:average_heartrate activity))))
       :drift drift
       :split split
       :cadence (:cadence agg)
       :trimp (load/trimp activity athlete)
       :band-pts (or (:band-pts trend) 0)})
    (catch Exception _e nil)))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            activities (repo/find-activities opts)

            hr-min (:hr-min opts)
            hr-max (:hr-max opts)
            athlete (load/load-athlete)
            rows (->> (keep #(compute-row % hr-min hr-max athlete) activities)
                      (sort-by :date))
            show-pct (:baseline-window opts)
            rows (if show-pct
                   (mapv (fn [r]
                           (let [bl (analysis/baseline-ef @repo/activities
                                                          (:baseline-window opts)
                                                          (:decay opts)
                                                          (:date r))]
                             (assoc r :ef-pct (when (and (:ef r) bl (pos? bl))
                                                (* 100.0 (/ (double (:ef r)) bl))))))
                         rows)
                   rows)]
        (if (seq rows)
          (let [rows (cond->> rows
                       show-pct (mapv #(update % :ef-pct (fn [v] (if v (format "%.0f%%" v) "-")))))
                rows (mapv format-row rows)
                header (cond-> ["Date" "Dist" "Time" "HR" "Pace" "EF" "Drift" "Split" "Cad" "TRIMP"]
                         show-pct (conj "EF%")
                         true (conj "Name"))
                keys (cond-> [:date :distance :duration :avg-hr :avg-pace :ef :drift :split :cadence :trimp]
                       show-pct (conj :ef-pct)
                       true (conj :name))]
            (println (if (and hr-min hr-max)
                       (format "Compare %d activities  |  HR band: %d-%d" (count rows) hr-min hr-max)
                       (format "Compare %d activities" (count rows))))
            (table/print-table header keys rows))
          (println "No activities found")))))

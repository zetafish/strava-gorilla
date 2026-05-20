(ns strava.cli.trend
  (:require [babashka.cli :as cli]
            [strava.analysis :as analysis]
            [strava.repo :as repo]
            [strava.table :as table]
            [strava.track :as track]))

(def spec {:pattern {:alias :p :coerce [] :require true}
           :n {:coerce :long :default 20 :desc "Max number of activities"}
           :hr-min {:coerce :long :default 130 :desc "HR band lower bound"}
           :hr-max {:coerce :long :default 140 :desc "HR band upper bound"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb trend -p <pattern> [-n N] [--hr-min 130] [--hr-max 140]")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn compute-summary [activity hr-min hr-max]
  (try
    (let [f (repo/get-fit-file-by-activity activity)
          records (-> (track/parse-file f) track/add-duration track/remove-head track/remove-tail)]
      (when-let [stats (analysis/trend-summary records hr-min hr-max)]
        (merge stats
               {:date (some-> (:start_date_local activity) (subs 0 10))
                :name (:name activity)
                :distance (/ (:distance activity) 1000.0)})))
    (catch Exception _e nil)))

(defn pace->str [seconds]
  (when seconds
    (format "%d:%02d" (quot seconds 60) (mod seconds 60))))

(defn format-row [s]
  (-> s
      (update :distance #(format "%.1f" %))
      ;; (update :heart_rate str)
      (update :pace pace->str)
      ;; (update :ef-band #(if % (format "%.3f" %) "-"))
      (update :band-pts #(str (or % 0)))))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            activities (->> (mapcat #(repo/find-by-pattern %) (:pattern opts))
                            distinct
                            (sort-by :start_date #(compare %2 %1))
                            (take (:n opts)))
            hr-min (:hr-min opts)
            hr-max (:hr-max opts)
            summaries (->> (keep #(compute-summary % hr-min hr-max) activities)
                           (sort-by :date))]
        (if (seq summaries)
          (do (println (format "EF trend at HR %d-%d  (%d activities)" hr-min hr-max (count summaries)))
              (table/print-table
               ["Date" "Dist" "HR" "Pace" "EF" "pts" "Name"]
               [:date :distance :heart_rate :pace :ef :pts :name]
               (mapv format-row summaries)))
          (println "No activities found for" (:pattern opts))))))

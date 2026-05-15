(ns strava.cli.trend
  (:require [babashka.cli :as cli]
            [strava.repo :as repo]
            [strava.track :as track]
            [strava.analysis :as analysis]))

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

(defn compute-summary [f hr-min hr-max]
  (try
    (let [activity (some-> (repo/extract-id f) repo/find-activity)
          records (-> (track/parse-file f) track/add-duration track/remove-head track/remove-tail)]
      (when-let [stats (and activity (analysis/trend-summary records hr-min hr-max))]
        (merge stats
               {:date (some-> (:start_date_local activity) (subs 0 10))
                :name (:name activity)
                :distance (/ (:distance activity) 1000.0)})))
    (catch Exception _e nil)))

(defn pace->str [seconds]
  (when seconds
    (format "%d:%02d" (quot seconds 60) (mod seconds 60))))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            files (->> (mapcat #(repo/find-by-pattern %) (:pattern opts))
                       distinct
                       (sort-by str #(compare %2 %1))
                       (take (:n opts)))
            hr-min (:hr-min opts)
            hr-max (:hr-max opts)
            summaries (->> (keep #(compute-summary % hr-min hr-max) files)
                           (sort-by :date))]
        (if (seq summaries)
          (let [sep (apply str (repeat 80 \-))]
            (println (format "EF trend at HR %d-%d  (%d activities)" hr-min hr-max (count summaries)))
            (println sep)
            (println (format "%-12s %6s %4s %6s %6s %5s  %s" "Date" "Dist" "HR" "Pace" "EF" "pts" "Name"))
            (println sep)
            (doseq [s summaries]
              (println (format "%-12s %6.1f %4d %6s %6s %5d  %s"
                               (:date s)
                               (:distance s)
                               (:avg-hr s)
                               (or (pace->str (:avg-pace s)) "-")
                               (if (:ef-band s)
                                 (format "%.3f" (:ef-band s))
                                 "-")
                               (or (:band-pts s) 0)
                               (:name s)))))
          (println "No activities found for" (:pattern opts))))))

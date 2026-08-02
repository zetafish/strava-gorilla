(ns strava.cli.report-periods
  (:require [babashka.cli :as cli]
            [strava.search :as search]
            [strava.stats :as stats]
            [strava.table :as table]))

(def spec {:from {:desc "Start date (YYYY-MM-DD)"}
           :to {:desc "End date (YYYY-MM-DD)"}
           :help {:alias :h :coerce :boolean}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println (cli/format-opts {:spec spec}))
    true))

(defn month-key [activity]
  (-> activity :date (subs 0 7)))

(defn week-key [activity]
  (let [d (java.time.LocalDate/parse (:date activity))
        dow (.getValue (.getDayOfWeek d))
        monday (.minusDays d (dec dow))]
    (str monday)))

(defn day-key [activity]
  (-> activity :date (subs 0 10)))

(defn periodic-stats [args group-fn]
  (let [opts (cli/parse-opts args {:spec spec})
        activities (search/find-activities {:from (:from opts)
                                            :to (:to opts)
                                            :limit 10000})
        by-period (->> activities
                       (group-by group-fn)
                       (sort-by key)
                       (map (fn [[p acts]]
                              (assoc (stats/aggregate-activities acts) :period p))))]
    (when (seq by-period)
      (table/print-table
       [:period :runs :moving :elapsed
        :covered :speed :pace :kmph
        :step-length :cadence :heart-rate :ef]
       by-period))))

(defn run-daily [args]
  (or (help-requested args)
      (periodic-stats args day-key)))

(defn run-monthly [args]
  (or (help-requested args)
      (periodic-stats args month-key)))

(defn run-weekly [args]
  (or (help-requested args)
      (periodic-stats args week-key)))

(ns strava.cli.report-periods
  (:require [babashka.cli :as cli]
            [strava.search :as search]
            [strava.stats :as stats]
            [strava.table :as table]
            [strava.util :as u])
  (:import (java.time
            LocalDate)
           (java.time.temporal
            IsoFields)))

(def spec {:from {:desc "Start date (YYYY-MM-DD)" :coerce u/parse-from}
           :to {:desc "End date (YYYY-MM-DD)" :coerce u/parse-to}
           :range {}
           :help {:alias :h :coerce :boolean}})

(defn year-key [activity]
  (-> activity :date (subs 0 4)))

(defn quarter-key [activity]
  (let [q (-> (LocalDate/parse (:date activity))
              (.get IsoFields/QUARTER_OF_YEAR))]
    (str (year-key activity) "-Q" q)))

(defn month-key [activity]
  (-> activity :date (subs 0 7)))

(defn week-key [activity]
  (let [d (LocalDate/parse (:date activity))
        dow (.getValue (.getDayOfWeek d))
        monday (.minusDays d (dec dow))]
    (str monday)))

(defn day-key [activity]
  (-> activity :date (subs 0 10)))

(defn periodic-stats [args group-fn]
  (let [opts (-> (cli/parse-opts args {:spec spec})
                 u/expand-range)
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
  (or (u/help-requested args spec)
      (periodic-stats args day-key)))

(defn run-weekly [args]
  (or (u/help-requested args spec)
      (periodic-stats args week-key)))

(defn run-monthly [args]
  (or (u/help-requested args spec)
      (periodic-stats args month-key)))

(defn run-quarterly [args]
  (or (u/help-requested args spec)
      (periodic-stats args quarter-key)))

(defn run-yearly [args]
  (or (u/help-requested args spec)
      (periodic-stats args year-key)))

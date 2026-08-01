(ns strava.cli.monthly
  (:require [babashka.cli :as cli]
            [strava.repo :as repo]
            [strava.search :as search]
            [strava.table :as table]
            [strava.util :refer [avg speed->pace speed->kmph ef]]))

(def spec {:from {:desc "Start date (YYYY-MM-DD)"}
           :to {:desc "End date (YYYY-MM-DD)"}
           :help {:alias :h :coerce :boolean}})

(defn month-key [activity]
  (-> activity :date (subs 0 7)))

(defn week-key [activity]
  (let [d (java.time.LocalDate/parse (:date activity))
        dow (.getValue (.getDayOfWeek d))
        monday (.minusDays d (dec dow))]
    (str monday)))

(defn aggregate [activities]
  (let [total-dist (reduce + (map :distance activities))
        total-time (reduce + (map :moving_time activities))
        samples (->> activities
                     (pmap (comp repo/get-track :id))
                     (apply concat))
        heart-rate (avg :heart_rate samples)
        speed (/ total-dist total-time)]
    {:period nil
     :runs (count activities)
     :distance total-dist
     :duration total-time

     :heart_rate heart-rate
     :cadence (avg :cadence samples)
     :step_length (avg :step_length samples)

     :speed speed
     :pace (speed->pace speed)
     :kmph (speed->kmph speed)
     :ef (ef speed heart-rate)}))

(defn periodic-stats [args period-label group-fn]
  (when (some #{"--help" "-h"} args)
    (println (str "Usage: bb stats:" period-label " [OPTIONS]"))
    (println)
    (println (cli/format-opts {:spec spec}))
    (System/exit 0))
  (let [opts (cli/parse-opts args {:spec spec})
        activities (search/find-activities {:from (:from opts)
                                            :to (:to opts)
                                            :limit 10000})
        by-period (->> activities
                       (group-by group-fn)
                       (sort-by key)
                       (pmap (fn [[p acts]]
                               (assoc (aggregate acts) :period p))))]
    (when (seq by-period)
      (table/print-table
       [:period :runs :distance :duration :heart_rate :pace :kmph :ef :step_length :cadence]
       by-period))))

(defn run [args]
  (periodic-stats args "month" month-key))

(defn run-weekly [args]
  (periodic-stats args "week" week-key))

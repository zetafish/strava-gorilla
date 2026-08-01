(ns strava.cli.monthly
  (:require [babashka.cli :as cli]
            [strava.search :as search]
            [strava.stats :as stats]
            [strava.table :as table]
            [strava.util :refer [speed->pace speed->kmph ef]]))

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

(defn weighted-avg [stats k]
  (let [stats (filter k stats)
        numerator (reduce + (map #(* (:sample-count %) (k %)) stats))
        denominator (reduce + (map :sample-count stats))]
    (when (pos? denominator)
      (/ numerator denominator))))

(defn sum [coll k]
  (reduce + (keep k coll)))

(defn aggregate [activities]
  (let [stats (->> (map :id activities)
                   (map #(stats/get-track-stats %)))
        hr (weighted-avg stats :heart_rate)
        cad (weighted-avg stats :cadence)
        sl (weighted-avg stats :step_length)
        distance (sum stats :distance)
        elapsed (sum stats :elapsed)
        moving (sum stats :moving)
        covered (sum stats :covered)
        speed (/ covered moving)]
    {:runs (count activities)
     :distance distance
     :elapsed elapsed
     :covered covered
     :moving moving
     :heart_rate hr
     :cadence cad
     :step_length sl
     :speed speed
     :pace (-> speed speed->pace)
     :kmph (-> speed speed->kmph)
     :ef (ef speed hr)}))

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
                       (map (fn [[p acts]]
                              (assoc (aggregate acts) :period p))))]
    (when (seq by-period)
      (table/print-table
       [:period :runs :distance :covered :moving :elapsed :heart_rate :speed
        :pace :kmph :ef :step_length :cadence]
       by-period))))

(defn run [args]
  (periodic-stats args "month" month-key))

(defn run-weekly [args]
  (periodic-stats args "week" week-key))

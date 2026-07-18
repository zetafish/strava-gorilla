(ns strava.cli.monthly
  (:require [babashka.cli :as cli]
            [clojure.string :as str]
            [strava.repo :as repo]
            [strava.table :as table]))

(def spec {:from {:desc "Start date (YYYY-MM-DD)"}
           :to {:desc "End date (YYYY-MM-DD)"}
           :help {:alias :h :coerce :boolean}})

(defn month-key [activity]
  (subs (:start_date activity) 0 7))

(defn week-key [activity]
  (let [d (java.time.LocalDate/parse (subs (:start_date activity) 0 10))
        dow (.getValue (.getDayOfWeek d))
        monday (.minusDays d (dec dow))]
    (str monday)))

(defn aggregate [activities]
  (let [total-dist (reduce + (map :distance activities))
        total-time (reduce + (map :moving_time activities))
        with-hr (filter :has_heartrate activities)
        avg-hr (when (seq with-hr)
                 (/ (reduce + (map :average_heartrate with-hr)) (count with-hr)))
        avg-speed (when (pos? total-time) (/ total-dist total-time))
        avg-pace (when (and avg-speed (pos? avg-speed)) (/ 1000.0 avg-speed))
        with-cad (filter #(some-> (:average_cadence %) pos?) activities)
        avg-cad (when (seq with-cad)
                  (* 2 (/ (reduce + (map :average_cadence with-cad)) (count with-cad))))
        with-ef (filter #(and (:has_heartrate %) (pos? (:average_heartrate %)) (pos? (:average_speed %))) activities)
        ef (when (seq with-ef)
             (let [total-t (reduce + (map :moving_time with-ef))
                   agg-speed (/ (reduce + (map :distance with-ef)) total-t)
                   agg-hr (/ (reduce + (map (fn [a] (* (:average_heartrate a) (:moving_time a))) with-ef)) total-t)]
               (when (pos? agg-hr)
                 (* 60.0 (/ agg-speed agg-hr)))))]
    {:period nil
     :runs (count activities)
     :distance total-dist
     :duration total-time
     :heart_rate (some-> avg-hr Math/round)
     :pace (some-> avg-pace Math/round)
     :ef ef
     :cadence (some-> avg-cad Math/round)}))

(defn periodic-stats [args period-label group-fn]
  (when (some #{"--help" "-h"} args)
    (println (str "Usage: bb stats:" period-label " [OPTIONS]"))
    (println)
    (println (cli/format-opts {:spec spec}))
    (System/exit 0))
  (let [opts (cli/parse-opts args {:spec spec})
        activities (repo/find-activities {:from (:from opts)
                                          :to (:to opts)
                                          :limit 10000})
        by-period (->> activities
                       (group-by group-fn)
                       (sort-by key)
                       (map (fn [[p acts]]
                              (assoc (aggregate acts) :period p))))]
    (when (seq by-period)
      (table/print-table
       [(str/capitalize period-label) "Runs" "Dist" "Dur" "HR" "Pace" "EF" "Cad"]
       [:period :runs :distance :duration :heart_rate :pace :ef :cadence]
       by-period))))

(defn run [args]
  (periodic-stats args "month" month-key))

(defn run-weekly [args]
  (periodic-stats args "week" week-key))

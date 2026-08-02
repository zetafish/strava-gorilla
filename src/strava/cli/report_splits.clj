(ns strava.cli.report-splits
  (:require [babashka.cli :as cli]
            [strava.format :as fmt]
            [strava.repo :as repo]
            [strava.table :as table]
            [strava.track :as track]
            [strava.util :as u]))

(defn print-summary [summary quarters activity]
  (when activity
    (println (format "  ID:         %s" (:id activity)))
    (println (format "  Name:       %s" (:name activity)))
    (println (format "  Start:      %s" (:start_date_local activity))))
  (println (format "  Distance:   %.1f km" (/ (:distance summary) 1000.0)))
  (println (format "  Duration:   %s" (fmt/at->str (:duration summary))))
  (println (format "  Avg HR:     %s" (:heart_rate summary)))
  (println (format "  Avg Pace:   %s" (fmt/pace->str (:pace summary))))
  (println (format "  Avg EF:     %s" (some-> (:ef_metric summary) (as-> v (format "%.3f" v)))))
  (let [efs (keep :ef_metric quarters)]
    (when (= 4 (count efs))
      (println (format "  EF Q1-Q4:   %.3f  %.3f  %.3f  %.3f"
                       (nth efs 0) (nth efs 1) (nth efs 2) (nth efs 3)))
      (let [decline (* 100.0 (/ (- (first efs) (last efs)) (first efs)))]
        (println (format "  EF Decline: %.1f%%" decline))))))

(def spec {:id {:coerce :long :desc "Activity ID"}
           :race {}
           :by-time {:alias :t :coerce u/parse-time}
           :by-distance {:alias :d :coerce u/parse-distance}
           :by-even {:alias :e :coerce :int}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println (cli/format-opts {:spec spec}))
    true))

(defn run [{:keys [id by-time by-distance by-even race]}]
  (let [act (repo/get-activity id)
        track (repo/get-track id)
        opts (cond
               by-time {:by :time :time by-time}
               by-distance {:by :distance :distance by-distance}
               by-even {:by :even :even by-even}
               :else {:by :distance :distance 1000})
        splits (track/splits (assoc opts :mode (when race :race)) track)]
    (println (:title act))
    (table/print-table [:from :to :moving :elapsed
                        :covered :speed :kmph :pace
                        :step-length :cadence :heart-rate :ef :efr]
                       splits)))

(defn -main [& args]
  (or (help-requested args)
      (run (cli/parse-opts args {:spec spec}))))

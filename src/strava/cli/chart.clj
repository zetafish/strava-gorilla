(ns strava.cli.chart
  (:require [babashka.cli :as cli]
            [strava.chart :as chart]
            [strava.search :as search]
            [strava.stats :as stats]
            [strava.util :as u]))

(def spec {:metric {:default "pace" :desc "Metric to plot: pace, kmph, speed, heart-rate, ef, cadence, step-length"}
           :from {:coerce u/parse-from}
           :to {:coerce u/parse-to}
           :range {}
           :width {:coerce :long :default 900}
           :height {:coerce :long :default 300}
           :file {:desc "Output SVG path" :default "scratch/chart.svg"}})

(defn chart [opts]
  (let [opts (u/expand-range opts)
        metric (keyword (:metric opts))
        activities (->> (search/find-activities opts)
                        (sort-by :date)
                        (map (fn [act]
                               (let [m (stats/get-track-stats (:id act) opts)]
                                 (-> m
                                     (assoc :speed (when (some-> (:moving m) pos?) (/ (:covered m) (:moving m))))
                                     u/with-derived-metrics)))))
        file (chart/svg-line-chart activities
                                   {:metric metric
                                    :width (:width opts)
                                    :height (:height opts)
                                    :file (:file opts)
                                    :title (name metric)})]
    (if file
      (println "Wrote" file)
      (println "No data for metric" (:metric opts)))))

(defn -main [& args]
  (or (u/help-requested args spec)
      (chart (cli/parse-opts args {:spec spec}))))

(ns strava.cli.report-activities
  (:require [babashka.cli :as cli]
            [strava.search :as search]
            [strava.stats :as stats]
            [strava.table :as table]
            [strava.util :as u]))

(def spec {:pattern {:alias :p :desc "Match part of the name"}
           :dist-min {:coerce :int :desc "Min distance in km"}
           :dist-max {:coerce :int :desc "Max distance in km"}
           :from {:desc "Activities since yyyy-mm-dd" :coerce u/parse-from}
           :to {:desc "Activities until yyyy-mm-dd" :coerce u/parse-to}
           :speed-min {:coerce :double :desc "Min moving speed in km/h"}
           :speed-max {:coerce :double :desc "Max moving speed in km/h"}
           :pace-min {:coerce u/pace->kmph :desc "Slowest moving pace, m:ss per km"}
           :pace-max {:coerce u/pace->kmph :desc "Fastest moving pace, m:ss per km"}
           :range {}
           :rebuild-stats {}})

(defn print-table [coll]
  (table/print-table
   [:timestamp :id :moving :elapsed
    :gait :covered  :speed :kmph :pace
    :step-length :cadence :heart-rate :ef :title]
   coll))

(defn speed-bounds
  "Combine speed and pace options into [lo hi] km/h, either may be nil."
  [{:keys [speed-min speed-max pace-min pace-max]}]
  (let [lo (or speed-min pace-min)
        hi (or speed-max pace-max)]
    (if (and lo hi (> lo hi)) [hi lo] [lo hi])))

(defn filter-speed [opts coll]
  (let [[lo hi] (speed-bounds opts)]
    (cond->> coll
      lo (filter #(some-> (:kmph %) (>= lo)))
      hi (filter #(some-> (:kmph %) (<= hi))))))

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb search [OPTIONS]")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn run [args]
  (or (help-requested args)
      (let [opts (-> (cli/parse-opts args {:spec spec}) u/expand-range)
            _ (println opts)
            acts (search/find-activities opts)
            data (map (fn [act]
                        (let [m (stats/get-track-stats (:id act) opts)]
                          (-> m
                              (assoc :id (:id act)
                                     :title (:title act)
                                     :speed (when (pos? (:moving m)) (/ (:covered m) (:moving m))))
                              u/with-derived-metrics)))
                      acts)]
        (print-table (filter-speed opts data)))))

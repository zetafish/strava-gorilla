(ns strava.cli.report-activities
  (:require [babashka.cli :as cli]
            [strava.search :as search]
            [strava.stats :as stats]
            [strava.table :as table]
            [strava.track :as track]))

(def spec {:pattern {:alias :p :desc "Match part of the name"}
           :dist-min {:coerce :int :desc "Min distance in km"}
           :dist-max {:coerce :int :desc "Max distance in km"}
           :from {:desc "Activities since yyyy-mm-dd"}
           :to {:desc "Activities until yyyy-mm-dd"}})

(defn print-table [coll]
  (println (first coll))
  (table/print-table
   [:timestamp :id :elapsed :moving :distance  :speed :kmph :pace
    :cadence :step-length :heart-rate :ef :title]
   coll))

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb search [OPTIONS]")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            acts (search/find-activities opts)
            data (map (fn [act]
                        (let [m (stats/get-track-stats (:id act))]
                          (-> m
                              (assoc :id (:id act)
                                     :title (:title act)
                                     :speed (/ (:covered m) (:moving m)))
                              track/derive-stats)))
                      acts)]
        (print-table data))))

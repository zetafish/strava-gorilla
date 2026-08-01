(ns strava.cli.search
  (:require [babashka.cli :as cli]
            [strava.repo :as repo]
            [strava.search :as search]
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
   [:timestamp :id :elapsed :moving :distance :cadence :speed :kmph :pace
    :heart_rate :ef :title]
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
            data (pmap (fn [act]
                         (let [track (repo/get-track (:id act))]
                           (-> (track/summary track)
                               (assoc :title (:title act)
                                      :id (:id act)))))
                       acts)]
        (print-table data))))

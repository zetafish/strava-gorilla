(ns strava.cli.report-gait
  (:require [babashka.cli :as cli]
            [strava.repo :as repo]
            [strava.table :as table]
            [strava.track :as track]
            [strava.util :as u]))

(def spec {:id {:coerce :long :desc "Activity ID"}
           :min {:coerce u/parse-time :desc "Shortest stretch kept as its own split (default 60s)"}
           :smooth {:coerce :int :desc "Majority-vote window in samples for gait (default 31)"}
           :ef-skip-warmup {:desc "Blank EF until HR stabilizes"}})

(defn totals
  "One row per gait, summing the stretches of that gait."
  [splits]
  (->> (group-by :gait splits)
       (sort-by key)
       (map (fn [[gait ss]]
              (let [moving (u/sum ss :moving)
                    covered (u/sum ss :covered)
                    weighted (fn [k]
                               (let [ss (filter k ss)
                                     n (u/sum ss :sample-count)]
                                 (when (pos? n)
                                   (/ (reduce + (map #(* (:sample-count %) (k %)) ss)) n))))]
                (u/with-derived-metrics
                 {:gait gait
                  :stretches (count ss)
                  :moving moving
                  :elapsed (u/sum ss :elapsed)
                  :covered covered
                  :speed (when (pos? moving) (/ covered moving))
                  :step-length (weighted :step-length)
                  :cadence (weighted :cadence)
                  :heart-rate (weighted :heart-rate)}))))))

(defn run [{:keys [id min smooth ef-skip-warmup]}]
  (let [act (repo/get-activity id)
        opts (cond-> {:by :gait :ef-skip-warmup ef-skip-warmup}
               min (assoc :min-duration min)
               smooth (assoc :smooth smooth))
        splits (track/splits opts (repo/get-track id))]
    (println (:title act))
    (table/print-table [:from :to :gait :moving :elapsed
                        :covered :speed :kmph :pace
                        :step-length :cadence :heart-rate :ef]
                       splits)
    (println)
    (table/print-table [:gait :stretches :moving :elapsed
                        :covered :kmph :pace
                        :step-length :cadence :heart-rate :ef]
                       (totals splits))))

(defn -main [& args]
  (or (u/help-requested args spec)
      (run (cli/parse-opts args {:spec spec}))))

(ns strava.cli.eff
  (:require [babashka.cli :as cli]
            [clojure.string :as str]
            [strava.analysis :as analysis]
            ;; [strava.cli.common :as common]
            [strava.format :as fmt]
            [strava.repo :as repo]
            [strava.table :as table]
            [strava.track :as track]))

(defn format-point [m] m)

(defn print-table [coll]
  (table/print-table
   ["Offset" "HR" "Pace" "km/h" "dist" "EF" "run%" "runEF" "walkEF" "cad" "pts"]
   [:at :heart_rate :pace :kmph :distance :ef_metric :run_pct :run_ef :walk_ef :cadence :pts]
   coll))

(defn- enrich-bucket
  "Given a bucket aggregate and its raw samples, add run/walk EF and %run."
  [agg samples]
  (let [by-gait (group-by :gait samples)
        run-samples (get by-gait :run)
        walk-samples (get by-gait :walk)
        run-agg (some-> run-samples seq analysis/agg)
        walk-agg (some-> walk-samples seq analysis/agg)
        moving (concat run-samples walk-samples)]
    (assoc agg
           :run_ef (:ef_metric run-agg)
           :walk_ef (:ef_metric walk-agg)
           :run_pct (when (seq moving)
                      (* 100.0 (/ (count run-samples) (double (count moving))))))))

(defn print-pauses [gaps]
  (table/print-table
   ["Paused at" "Resumed at" "Duration"]
   [:paused-at :resumed-at :duration]
   (map (fn [{:keys [at duration]}]
          {:paused-at (fmt/at->str at)
           :resumed-at (fmt/at->str (+ at duration))
           :duration duration})
        gaps)))

(defn print-walks [walks]
  (table/print-table
   ["From" "To" "Duration" "Distance"]
   [:from :to :duration :distance]
   (map (fn [{:keys [at duration distance]}]
          {:from (fmt/at->str at)
           :to (fmt/at->str (+ at duration))
           :duration duration
           :distance distance})
        walks)))

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
           ;; :pattern {:alias :p :coerce [] :desc "Match part of the name"}
           :interval {:alias :i :coerce analysis/parse-at :default 3600 :desc "Bucket interval (e.g. 5m, 10s, 1h)"}
           :format {:alias :f :default "table" :validate #{"table" "csv" "json"}}
           :from {:coerce analysis/parse-at
                  :default 0
                  :desc "Start time as offset (e.g. 21h15, 3h, 120m, 7200s)"}
           :to {:coerce analysis/parse-at
                :default Integer/MAX_VALUE
                :desc "End time as offset (e.g. 21h15, 3h, 120m, 7200s)"}
           :pause-speed {:coerce :double :default 0.5
                         :desc "Speed (m/s) at or below which the watch is considered not moving"}
           :pause-min {:coerce analysis/parse-at :default 60
                       :desc "Minimum stationary duration to count as a pause (e.g. 60s, 5m)"}
           :run-speed {:coerce :double :default 2.0
                       :desc "Min speed (m/s) to classify a sample as running"}
           :run-cadence {:coerce :long :default 140
                         :desc "Min cadence (spm, doubled) to classify a sample as running"}
           :walk-min {:coerce analysis/parse-at :default 60
                      :desc "Minimum walk duration to list in the walks table (e.g. 60s, 5m)"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println (cli/format-opts {:spec spec}))
    true))

(defn pause-stats [raw-track pause-speed pause-min]
  (let [elapsed (- (:at (last raw-track)) (:at (first raw-track)))
        pauses (track/merge-pauses
                (track/gaps raw-track)
                (track/stationary-runs raw-track pause-speed pause-min))
        paused (reduce + 0 (map :duration pauses))]
    {:elapsed elapsed
     :moving (- elapsed paused)
     :paused paused
     :pauses (count pauses)
     :gaps pauses}))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            ;; activity (repo/get-activity (:id opts))
            ;; date (subs (:start_date_local activity) 0 10)
            raw (->> (repo/get-track (:id opts))
                     (drop-while #(< (:at %) (:from opts)))
                     (take-while #(< (:at %) (:to opts))))
            stats (pause-stats raw (:pause-speed opts) (:pause-min opts))
            track (->> raw
                       (track/remove-pauses (:gaps stats))
                       (track/compress-pauses (:gaps stats))
                       track/rebase-at
                       (track/classify (:run-speed opts) (:run-cadence opts)))
            groups (analysis/bucketize-groups (:interval opts) track)
            buckets (map (fn [samples]
                           (enrich-bucket (analysis/agg samples) samples))
                         groups)
            q (quot (count track) 4)
            quarter-samples [(take q track)
                             (->> track (drop q) (take q))
                             (->> track (drop (* 2 q)) (take q))
                             (drop (* 3 q) track)]
            quarters (mapv (fn [samples]
                             (enrich-bucket (analysis/agg samples) samples))
                           quarter-samples)
            {:keys [elapsed moving paused pauses gaps]} stats]
        (print-table buckets)
        (println)
        (println (format "  Elapsed:    %s" (fmt/at->str elapsed)))
        (println (format "  Moving:     %s" (fmt/at->str moving)))
        (println (format "  Paused:     %s (%d gaps)" (fmt/at->str paused) pauses))
        (when (seq gaps)
          (println)
          (println "  Pauses:")
          (print-pauses gaps))
        (let [walks (track/walk-runs track (:walk-min opts))]
          (when (seq walks)
            (println)
            (println (format "  Walks (>= %ss):" (:walk-min opts)))
            (print-walks walks)))
        (let [efs (mapv :ef_metric quarters)
              run-efs (mapv :run_ef quarters)]
          (when (= 4 (count (keep identity efs)))
            (println)
            (println (format "  EF Q1-Q4:      %.3f  %.3f  %.3f  %.3f"
                             (nth efs 0) (nth efs 1) (nth efs 2) (nth efs 3)))
            (let [decline (* 100.0 (/ (- (first efs) (last efs)) (first efs)))]
              (println (format "  EF Decline:    %.1f%%" decline)))
            (when (every? some? run-efs)
              (println (format "  runEF Q1-Q4:   %.3f  %.3f  %.3f  %.3f"
                               (nth run-efs 0) (nth run-efs 1) (nth run-efs 2) (nth run-efs 3)))
              (let [decline (* 100.0 (/ (- (first run-efs) (last run-efs)) (first run-efs)))]
                (println (format "  runEF Decline: %.1f%%" decline))))
            (println (format "  Run%%:          %s"
                             (str/join "  "
                                       (map #(if (:run_pct %)
                                               (format "%3d%%" (int (:run_pct %)))
                                               "   -")
                                            quarters))))))

        #_(if-let [activity (cond
                              (:id opts) (repo/get-activity (:id opts))
                              (:pattern opts) (->> (mapcat repo/find-by-pattern (:pattern opts))
                                                   (sort-by :start_date #(compare %2 %1))
                                                   first))]
            (let [records (cond->> (repo/track (:id activity))
                            (:from opts) (drop-while #(< (:at %) (:from opts)))
                            (:to opts) (take-while #(< (:at %) (:to opts))))
                  date (subs (:start_date_local activity) 0 10)
                  coll (->> (analysis/bucketize (:interval opts) records)
                            (map #(assoc % :date date))
                            (map format-point))
                  summary (-> (analysis/agg records) format-point)
                  q (quot (count records) 4)
                  quarters (mapv analysis/agg
                                 [(take q records)
                                  (->> records (drop q) (take q))
                                  (->> records (drop (* 2 q)) (take q))
                                  (drop (* 3 q) records)])]
              (print-table coll)
              (print-summary summary quarters activity))
            (println "No activity found for" (or (:id opts) (:pattern opts)))))))

;; (repo/get-activity 19346497813)
;; (run ["--id" "19346497813"])

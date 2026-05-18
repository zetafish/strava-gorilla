(ns strava.cli.compare
  (:require [babashka.cli :as cli]
            [strava.analysis :as analysis]
            [strava.repo :as repo]
            [strava.tags :as tags]
            [strava.track :as track]))

(def spec {:pattern {:alias :p :coerce []}
           :tag {:alias :t :coerce :keyword :desc "Filter by tag"}
           :no-tag {:alias :T :coerce :keyword :desc "Exclude activities with tag"}
           :above {:coerce :double :desc "Min distance in km (exclusive)"}
           :below {:coerce :double :desc "Max distance in km (exclusive)"}
           :n {:coerce :long :default 20 :desc "Max number of activities"}
           :hr-min {:coerce :long :default 110 :desc "HR band lower bound"}
           :hr-max {:coerce :long :default 160 :desc "HR band upper bound"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb compare -p <pattern> [--above KM] [--below KM] [--tag TAG] [-n N]")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn pace->str [seconds]
  (when seconds
    (format "%d:%02d" (quot seconds 60) (mod seconds 60))))

(defn duration->str [seconds]
  (let [h (quot seconds 3600)
        m (rem (quot seconds 60) 60)]
    (format "%d:%02d" h m)))

(defn cardiac-drift [records]
  (let [n (count records)
        third (quot n 3)]
    (when (>= third 10)
      (let [first-hrs (keep :heart_rate (take third records))
            last-hrs (keep :heart_rate (drop (* 2 third) records))]
        (when (and (seq first-hrs) (seq last-hrs))
          (let [avg-first (/ (reduce + first-hrs) (double (count first-hrs)))
                avg-last (/ (reduce + last-hrs) (double (count last-hrs)))]
            (* 100.0 (/ (- avg-last avg-first) avg-first))))))))

(defn positive-split [records]
  (let [n (count records)
        half (quot n 2)]
    (when (>= half 10)
      (let [first-half (take half records)
            second-half (drop half records)
            avg-pace (fn [recs]
                       (let [paces (keep #(when (pos? (:speed % 0)) (analysis/pace %)) recs)]
                         (when (seq paces)
                           (/ (reduce + paces) (double (count paces))))))]
        (when-let [p1 (avg-pace first-half)]
          (when-let [p2 (avg-pace second-half)]
            (/ p2 p1)))))))

(defn compute-row [f hr-min hr-max]
  (try
    (let [activity (some-> (repo/extract-id f) repo/find-activity)
          records (-> (track/parse-file f) track/add-duration track/remove-head track/remove-tail)
          agg (analysis/agg records)
          trend (when activity (analysis/trend-summary records hr-min hr-max))
          drift (cardiac-drift records)
          split (positive-split records)]
      (when activity
        {:date (some-> (:start_date_local activity) (subs 0 10))
         :name (:name activity)
         :distance (/ (:distance activity) 1000.0)
         :duration (:duration agg)
         :avg-hr (:heart_rate agg)
         :avg-pace (analysis/pace agg)
         :ef (or (:ef-band trend) (:ef_metric (analysis/enrich agg)))
         :drift drift
         :split split
         :cadence (:cadence agg)
         :band-pts (or (:band-pts trend) 0)}))
    (catch Exception _e nil)))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            files (apply repo/find-by-pattern (or (seq (:pattern opts)) [""]))
            tag-filter (:tag opts)
            no-tag (:no-tag opts)
            above (:above opts)
            below (:below opts)
            needs-activities (or tag-filter no-tag above below)
            activity-by-id (when needs-activities
                             (into {} (map (juxt :id identity)) (tags/load-all-activities)))
            manual-tags (when (or tag-filter no-tag) (tags/load-tags))
            filtered (cond->> files
                       tag-filter
                       (filter (fn [f]
                                 (when-let [id (repo/extract-id f)]
                                   (let [activity (get activity-by-id id)
                                         all (if activity
                                               (into (get manual-tags id #{}) (tags/auto-tags activity))
                                               (get manual-tags id #{}))]
                                     (contains? all tag-filter)))))
                       no-tag
                       (remove (fn [f]
                                 (when-let [id (repo/extract-id f)]
                                   (let [activity (get activity-by-id id)
                                         all (if activity
                                               (into (get manual-tags id #{}) (tags/auto-tags activity))
                                               (get manual-tags id #{}))]
                                     (contains? all no-tag)))))
                       above
                       (filter (fn [f]
                                 (when-let [id (repo/extract-id f)]
                                   (when-let [activity (get activity-by-id id)]
                                     (> (/ (:distance activity 0) 1000.0) above)))))
                       below
                       (filter (fn [f]
                                 (when-let [id (repo/extract-id f)]
                                   (when-let [activity (get activity-by-id id)]
                                     (< (/ (:distance activity 0) 1000.0) below))))))
            selected (->> filtered
                          (sort-by str #(compare %2 %1))
                          (take (:n opts)))
            hr-min (:hr-min opts)
            hr-max (:hr-max opts)
            rows (->> (keep #(compute-row % hr-min hr-max) selected)
                      (sort-by :date))]
        (if (seq rows)
          (let [sep (apply str (repeat 96 \-))]
            (println (format "Compare %d activities  |  HR band: %d-%d" (count rows) hr-min hr-max))
            (println sep)
            (println (format "%-12s %6s %6s %4s %6s %6s %6s %6s %5s  %s"
                             "Date" "Dist" "Time" "HR" "Pace" "EF" "Drift" "Split" "Cad" "Name"))
            (println sep)
            (doseq [r rows]
              (println (format "%-12s %5.1fk %6s %4d %6s %6s %5s%% %6s %5d  %s"
                               (:date r)
                               (:distance r)
                               (duration->str (or (:duration r) 0))
                               (or (:avg-hr r) 0)
                               (or (pace->str (:avg-pace r)) "-")
                               (if (:ef r) (format "%.3f" (double (:ef r))) "-")
                               (if (:drift r) (format "%+.1f" (:drift r)) "-")
                               (if (:split r) (format "%.2f" (:split r)) "-")
                               (or (:cadence r) 0)
                               (let [n (:name r "")]
                                 (subs n 0 (min (count n) 30)))))))
          (println "No activities found")))))

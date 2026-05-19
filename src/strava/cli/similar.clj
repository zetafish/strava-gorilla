(ns strava.cli.similar
  (:require [babashka.cli :as cli]
            [strava.analysis :as analysis]
            [strava.geo :as geo]
            [strava.repo :as repo]))

(def spec {:limit {:alias :n :coerce :long :default 10 :desc "Max results"}
           :threshold {:alias :d :coerce :long :default 200 :desc "Max deviation in meters"}
           :dist-tol {:coerce :double :default 0.3 :desc "Max distance ratio difference (0.3 = 30%)"}
           :from {:desc "Start date (YYYY-MM-DD)"}
           :to {:desc "End date (YYYY-MM-DD)"}
           :baseline-window {:coerce :long :desc "Baseline EF window in days (enables EF% column)"}
           :decay {:coerce :double :default 1.0 :desc "Daily decay factor for baseline EF (e.g. 0.999)"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb similar <activity-id> [OPTIONS]")
    (println)
    (println "Find runs with similar routes to a reference activity.")
    (println "Score = average route deviation in meters (lower = more similar).")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn get-polyline [activity]
  (some-> activity :map :summary_polyline))

(defn pace->str [activity]
  (when (and (:moving_time activity) (pos? (:distance activity 0)))
    (let [s (/ (* (:moving_time activity) 1000.0) (:distance activity))]
      (format "%d:%02d" (int (quot s 60)) (int (mod s 60))))))

(defn ef [activity]
  (when (and (:average_speed activity) (:average_heartrate activity)
             (pos? (:average_heartrate activity)))
    (* 60.0 (/ (:average_speed activity) (:average_heartrate activity)))))

(defn run [args]
  (or (help-requested args)
      (let [id (some-> (first args) parse-long)
            opts (cli/parse-opts (rest args) {:spec spec})]
        (if-not id
          (println "Error: provide an activity ID as first argument")
          (let [ref-activity (repo/find-activity id)]
            (if-not ref-activity
              (println "Activity not found:" id)
              (let [ref-poly (get-polyline ref-activity)]
                (if-not ref-poly
                  (println "Activity has no route data:" id)
                  (let [ref-points (geo/decode-polyline ref-poly)
                        ref-dist (:distance ref-activity)
                        dist-tol (:dist-tol opts)
                        candidates (->> @repo/activities
                                        (filter #(= "Run" (:sport_type %)))
                                        (filter #(get-polyline %))
                                        (filter #(<= (abs (- 1.0 (/ (:distance % 1) (max 1 ref-dist)))) dist-tol))
                                        (filter #(let [d (some-> (:start_date %) (subs 0 10))]
                                                   (and (or (not (:from opts)) (>= (compare d (:from opts)) 0))
                                                        (or (not (:to opts)) (<= (compare d (:to opts)) 0)))))
                                        (pmap (fn [a]
                                                (let [pts (geo/decode-polyline (get-polyline a))]
                                                  (when (>= (count pts) 2)
                                                    (assoc a :score (geo/route-similarity ref-points pts))))))
                                        (filter some?)
                                        (filter #(:score %))
                                        (filter #(<= (:score %) (:threshold opts)))
                                        (sort-by #(some-> (:start_date %) (subs 0 10)) #(compare %2 %1))
                                        (take (:limit opts)))]
                    (let [show-pct (:baseline-window opts)
                          baselines (when show-pct
                                      (into {} (keep (fn [a]
                                                       (when-let [d (some-> (:start_date a) (subs 0 10))]
                                                         [(:id a) (analysis/baseline-ef @repo/activities
                                                                                        (:baseline-window opts)
                                                                                        (:decay opts)
                                                                                        d)]))
                                                     candidates)))]
                      (println (format "Similar routes to \"%s\" (%s, %.1fkm)"
                                       (:name ref-activity)
                                       (some-> (:start_date ref-activity) (subs 0 10))
                                       (/ (:distance ref-activity) 1000.0)))
                      (let [sep (apply str (repeat (if show-pct 82 76) \─))]
                        (println sep)
                        (apply println
                               (format "%12s %12s %6s %6s %6s %4s %6s" "ID" "Date" "Dist" "Score" "Pace" "HR" "EF")
                               (if show-pct
                                 [(format " %5s  %s" "EF%" "Name")]
                                 [(format "  %s" "Name")]))
                        (println sep)
                        (if (seq candidates)
                          (doseq [a candidates]
                            (let [e (ef a)
                                  bl (get baselines (:id a))
                                  ef-pct (when (and e bl (pos? bl)) (* 100.0 (/ e bl)))]
                              (print (format "%12s %12s %5.1fk %5.0fm %6s %4s %6s"
                                             (:id a)
                                             (some-> (:start_date a) (subs 0 10))
                                             (/ (:distance a) 1000.0)
                                             (:score a)
                                             (or (pace->str a) "-")
                                             (if (:average_heartrate a)
                                               (format "%d" (int (:average_heartrate a)))
                                               "-")
                                             (if e (format "%.3f" e) "-")))
                              (when show-pct
                                (print (format " %4s" (if ef-pct (format "%.0f%%" ef-pct) "-"))))
                              (println (format "  %s" (let [n (:name a "")]
                                                        (subs n 0 (min (count n) 30)))))))
                          (println "No similar routes found")))))))))))))

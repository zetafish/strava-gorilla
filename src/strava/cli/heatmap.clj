(ns strava.cli.heatmap
  (:require [babashka.cli :as cli]
            [strava.analysis :as analysis]
            [strava.cli.common :as common]
            [strava.repo :as repo]))

(def spec {:pattern {:alias :p :coerce [] :require true}
           :metric1 {:default "pace" :desc "Y-axis metric: hr, ef, pace, cadence, step-length"}
           :metric2 {:default "hr" :desc "X-axis metric: hr, ef, pace, cadence, step-length"}
           :interval {:alias :i :coerce analysis/parse-at :default 60}
           :from {:coerce analysis/parse-at}
           :to {:coerce analysis/parse-at}
           :width {:coerce :long :default 40}
           :height {:coerce :long :default 20}
           :sd {:coerce :double :default 3 :desc "SD threshold for outliers (0 to disable)"}
           :sd-clip {:coerce :double :desc "Clip to N SDs from mean for display"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb heatmap -p <pattern> [--metric1 pace] [--metric2 hr] [-i interval]")
    (println)
    (println "Available metrics: hr, ef, pace, cadence, step-length")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn intensity-char [density]
  (cond
    (< density 0.2) " "
    (< density 0.4) "░"
    (< density 0.6) "▒"
    (< density 0.8) "▓"
    :else "█"))

(defn heatmap-plot [{:keys [width height]} metric1-key metric1-label metric2-key metric2-label values1 values2 clip-min1 clip-max1 clip-min2 clip-max2]
  (when (seq values1)
    (let [w (int width)
          h (int height)
          min1 (or clip-min1 (apply min values1))
          max1 (or clip-max1 (apply max values1))
          min2 (or clip-min2 (apply min values2))
          max2 (or clip-max2 (apply max values2))
          range1 (max 0.01 (- max1 min1))
          range2 (max 0.01 (- max2 min2))
          grid (vec (repeat h (vec (repeat w 0))))
          bin-size1 (/ range1 h)
          bin-size2 (/ range2 w)
          populated-grid
          (reduce
           (fn [g [v1 v2]]
             (let [bin-y (int (/ (- v1 min1) bin-size1))
                   bin-x (int (/ (- v2 min2) bin-size2))
                   y-clamped (max 0 (min (dec h) bin-y))
                   x-clamped (max 0 (min (dec w) bin-x))]
               (assoc-in g [y-clamped x-clamped] (inc (get-in g [y-clamped x-clamped] 0)))))
           grid
           (map vector values1 values2))
          max-count (apply max (conj (flatten populated-grid) 1))
          normalized-grid (mapv #(mapv (fn [c] (double (/ c max-count))) %) populated-grid)]
      (println)
      (println (format "%s vs %s (%d values)" metric1-label metric2-label (count values1)))
      (println (apply str (repeat (+ 10 w) \-)))

      (doseq [row (range h)]
        (let [val1 (+ min1 (* (- h row) bin-size1))]
          (print (format "%8s |" (common/format-axis-value metric1-key val1))))
        (doseq [col (range w)]
          (print (intensity-char (get-in normalized-grid [row col] 0))))
        (println))

      (println (str (apply str (repeat 10 " ")) (apply str (repeat w "-"))))
      (let [min-lbl (common/format-axis-value metric2-key min2)
            max-lbl (common/format-axis-value metric2-key max2)]
        (println (format "%8s %s%s%s"
                         " "
                         min-lbl
                         (apply str (repeat (- w (count min-lbl) (count max-lbl)) " "))
                         max-lbl))))))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            metric1 (keyword (:metric1 opts))
            metric2 (keyword (:metric2 opts))
            metric1-config (get common/metrics metric1)
            metric2-config (get common/metrics metric2)]
        (if (and metric1-config metric2-config)
          (let [files (->> (mapcat #(repo/find-by-pattern %) (:pattern opts))
                           distinct
                           (sort-by :start_date #(compare %2 %1))
                           (take 1)
                           (map repo/fit-file))]
            (if (seq files)
              (let [f (first files)
                    records (common/parse-file f)
                    bucketed (analysis/select-data opts records)
                    values1 (keep (:key metric1-config) bucketed)
                    values2 (keep (:key metric2-config) bucketed)
                    outliers1 (when (pos? (:sd opts)) (analysis/detect-outliers values1 (:sd opts)))
                    outliers2 (when (pos? (:sd opts)) (analysis/detect-outliers values2 (:sd opts)))
                    outlier-set1 (when outliers1 (set (:outliers outliers1)))
                    outlier-set2 (when outliers2 (set (:outliers outliers2)))
                    filtered-pairs (filter (fn [[v1 v2]]
                                             (and (or (not outlier-set1) (not (outlier-set1 v1)))
                                                  (or (not outlier-set2) (not (outlier-set2 v2)))))
                                           (map vector values1 values2))
                    filtered1 (map first filtered-pairs)
                    filtered2 (map second filtered-pairs)]
                (println (common/activity-label f))
                (let [clip-vals1 (when (:sd-clip opts)
                                   (when-let [{:keys [mean sd]} (analysis/compute-stats filtered1)]
                                     {:min (- mean (* (:sd-clip opts) sd))
                                      :max (+ mean (* (:sd-clip opts) sd))}))
                      clip-vals2 (when (:sd-clip opts)
                                   (when-let [{:keys [mean sd]} (analysis/compute-stats filtered2)]
                                     {:min (- mean (* (:sd-clip opts) sd))
                                      :max (+ mean (* (:sd-clip opts) sd))}))]
                  (when (and (seq filtered1) (seq filtered2))
                    (heatmap-plot opts
                                  (:key metric1-config) (:label metric1-config)
                                  (:key metric2-config) (:label metric2-config)
                                  filtered1 filtered2
                                  (:min clip-vals1) (:max clip-vals1)
                                  (:min clip-vals2) (:max clip-vals2)))
                  (when (pos? (:sd opts))
                    (let [removed (- (count values1) (count filtered1))]
                      (when (pos? removed)
                        (println)
                        (println (format "Removed %d outliers (>%.1f SD)" removed (double (:sd opts)))))))))
              (println "No fit file found for" (:pattern opts))))
          (println "Invalid metrics. Available: hr, ef, pace, cadence, step-length")))))

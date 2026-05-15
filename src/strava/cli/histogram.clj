(ns strava.cli.histogram
  (:require [babashka.cli :as cli]
            [strava.analysis :as analysis]
            [strava.cli.common :as common]
            [strava.repo :as repo]))

(def spec {:pattern {:alias :p :coerce [] :require true}
           :metric {:default "ef" :desc "Metric to plot: hr, ef, pace, cadence, step-length"}
           :n {:coerce :long :default 10 :desc "Number of buckets"}
           :interval {:alias :i :coerce analysis/parse-at :default 60}
           :from {:coerce analysis/parse-at}
           :to {:coerce analysis/parse-at}
           :width {:coerce :long :default 60}
           :height {:coerce :long :default 20}
           :sd {:coerce :double :default 3 :desc "SD threshold for outliers (0 to disable)"}
           :sd-clip {:coerce :double :desc "Clip to N SDs from mean (overrides clip-min/clip-max)"}
           :clip-min {:desc "Min value to display (clip values below this, use time format for pace)"}
           :clip-max {:desc "Max value to display (clip values above this, use time format for pace)"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb histogram -p <pattern> [--metric ef] [-n 10] [-i interval] [--from FROM] [--to TO]")
    (println)
    (println "Available metrics: hr, ef, pace, cadence, step-length")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn histogram-plot [{:keys [width height n]} metric-key metric-label values]
  (when (seq values)
    (let [h (int height)
          min-val (apply min values)
          max-val (apply max values)
          val-range (max 0.01 (- max-val min-val))
          bucket-size (/ val-range n)
          buckets (vec (repeat n 0))
          bucketed (reduce
                    (fn [acc v]
                      (let [bucket-idx (min (dec n) (int (/ (- v min-val) bucket-size)))]
                        (update acc bucket-idx inc)))
                    buckets
                    values)
          non-zero (filter pos? bucketed)
          max-count (or (apply max non-zero) 0)
          y-scale (if (pos? max-count) (/ (dec h) max-count) 1)]
      (println)
      (println (format "%s distribution (%d values)" metric-label (count values)))
      (println (apply str (repeat (+ 10 width) \-)))
      (doseq [row (reverse (range h))]
        (print (format "%3d " (int (* row (/ 1 y-scale)))))
        (print "|")
        (doseq [b bucketed]
          (if (pos? b)
            (let [bar-height (int (* b y-scale))]
              (if (>= bar-height row)
                (print "█")
                (print " ")))
            (print " ")))
        (println))
      (println (str (apply str (repeat 5 " ")) (apply str (repeat width "-"))))
      (let [min-lbl (common/format-axis-value metric-key min-val)
            max-lbl (common/format-axis-value metric-key max-val)]
        (println (format "%4s %s%s%s"
                         " "
                         min-lbl
                         (apply str (repeat (- width (count min-lbl) (count max-lbl)) " "))
                         max-lbl))))))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            metric (keyword (:metric opts))
            metric-config (get common/metrics metric)]
        (if metric-config
          (let [files (->> (mapcat #(repo/find-by-pattern %) (:pattern opts))
                           distinct
                           (sort-by str #(compare %2 %1))
                           (take 1))]
            (if (seq files)
              (let [f (first files)
                    records (common/parse-file f)
                    bucketed (analysis/select-data opts records)
                    values (keep (:key metric-config) bucketed)
                    metric-key (:key metric-config)
                    metric-label (:label metric-config)
                    outlier-info (when (pos? (:sd opts)) (analysis/detect-outliers values (:sd opts)))
                    filtered-values (if outlier-info
                                      (let [{:keys [lower upper]} outlier-info]
                                        (filter #(and (>= % lower) (<= % upper)) values))
                                      values)
                    sd-clip-vals (when (:sd-clip opts)
                                   (when-let [{:keys [mean sd]} (analysis/compute-stats filtered-values)]
                                     {:clip-min (- mean (* (:sd-clip opts) sd))
                                      :clip-max (+ mean (* (:sd-clip opts) sd))}))
                    clip-min-final (or (:clip-min sd-clip-vals) (common/parse-clip-value metric-key (:clip-min opts)))
                    clip-max-final (or (:clip-max sd-clip-vals) (common/parse-clip-value metric-key (:clip-max opts)))
                    clipped-values (analysis/clip-values filtered-values clip-min-final clip-max-final)
                    clip-info (analysis/count-clipped filtered-values clip-min-final clip-max-final)]
                (println (common/activity-label f))
                (histogram-plot opts metric-key metric-label clipped-values)
                (when (or (:below-min clip-info) (:above-max clip-info))
                  (println)
                  (when (:below-min clip-info)
                    (println (format "  (clipped %d values below %s)"
                                     (:below-min clip-info)
                                     (common/format-axis-value metric-key clip-min-final))))
                  (when (:above-max clip-info)
                    (println (format "  (clipped %d values above %s)"
                                     (:above-max clip-info)
                                     (common/format-axis-value metric-key clip-max-final)))))
                (when-let [{:keys [mean median sd count]} (analysis/compute-stats clipped-values)]
                  (println)
                  (println "Stats:")
                  (println (format "  Count:  %d" count))
                  (println (format "  Mean:   %s" (common/format-axis-value metric-key mean)))
                  (println (format "  Median: %s" (common/format-axis-value metric-key median)))
                  (println (format "  SD:     %s" (common/format-axis-value metric-key sd))))
                (when (and outlier-info (seq (:outliers outlier-info)))
                  (println)
                  (println (format "Outliers (>%.1f SD): %d values"
                                   (double (:sd opts)) (count (:outliers outlier-info))))
                  (doseq [v (sort (:outliers outlier-info))]
                    (println (format "  %s" (common/format-axis-value metric-key v))))))
              (println "No fit file found for" (:pattern opts))))
          (println "Invalid metric. Available: hr, ef, pace, cadence, step-length")))))

(ns strava.cli.line
  (:require [babashka.cli :as cli]
            ;; [strava.analysis :as analysis]
            ;; [strava.cli.common :as common]
            [strava.repo :as repo]
            [strava.util :as u]))

(def spec {:pattern {:alias :p :coerce [] :require true}
           :metric {:default "pace" :desc "Metric to plot: hr, ef, pace, cadence, step-length"}
           :interval {:alias :i :coerce u/parse-time :default 60}
           :from {:coerce u/parse-time}
           :to {:coerce u/parse-time}
           :width {:coerce :long :default 80}
           :height {:coerce :long :default 20}
           :sd-clip {:coerce :double :desc "Clip to N SDs from mean (overrides clip-min/clip-max)"}
           :clip-min {:desc "Min value to display (clip values below this, use time format for pace)"}
           :clip-max {:desc "Max value to display (clip values above this, use time format for pace)"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb line -p <pattern> [--metric pace] [-i interval] [--from FROM] [--to TO]")
    (println)
    (println "Available metrics: hr, ef, pace, cadence, step-length")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn format-time [secs]
  (let [h (int (quot secs 3600))
        m (int (quot (rem secs 3600) 60))
        s (int (rem secs 60))]
    (if (pos? h)
      (format "%dh%dm" h m)
      (format "%dm%ds" m s))))

(defn line-plot [{:keys [width height]} metric-key metric-label values times]
  (when (seq values)
    (let [h (int height)
          w (int width)
          min-val (apply min values)
          max-val (apply max values)
          val-range (max 0.01 (- max-val min-val))
          min-time (apply min times)
          max-time (apply max times)
          time-range (max 1 (- max-time min-time))
          x-scale (/ w time-range)
          y-scale (/ (dec h) val-range)
          canvas (vec (repeat h (vec (repeat w " "))))]
      (println)
      (println (format "%s over time (%d values)" metric-label (count values)))
      (println (apply str (repeat (+ 10 w) \-)))

      (let [updated-canvas
            (reduce
             (fn [c [t v]]
               (let [x (int (* (- t min-time) x-scale))
                     y (int (* (- v min-val) y-scale))
                     x-clamped (max 0 (min (dec w) x))
                     y-clamped (max 0 (min (dec h) y))]
                 (assoc-in c [(- (dec h) y-clamped) x-clamped] "•")))
             canvas
             (map vector times values))]

        (doseq [row (range h)]
          #_(let [val (+ min-val (/ (* (- (dec h) row) val-range) (dec h)))]
            (print (format "%8s |" (common/format-axis-value metric-key val))))
          (doseq [col (range w)]
            (print (get-in updated-canvas [row col] " ")))
          (println)))

      (println (str (apply str (repeat 10 " ")) (apply str (repeat w "-"))))
      (let [min-lbl (format-time min-time)
            max-lbl (format-time max-time)]
        (println (format "%8s %s%s%s"
                         " "
                         min-lbl
                         (apply str (repeat (- w (count min-lbl) (count max-lbl)) " "))
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
                    {:keys [values times clip-info clip-min clip-max metric-key metric-label]}
                    (common/prepare-line-data f opts metric-config)]
                (println (common/activity-label f))
                (when (or (:below-min clip-info) (:above-max clip-info))
                  (println)
                  (when (:below-min clip-info)
                    (println (format "  (clipped %d values below %s)"
                                     (:below-min clip-info)
                                     (common/format-axis-value metric-key clip-min))))
                  (when (:above-max clip-info)
                    (println (format "  (clipped %d values above %s)"
                                     (:above-max clip-info)
                                     (common/format-axis-value metric-key clip-max)))))
                (when (seq values)
                  (line-plot opts metric-key metric-label values times)))
              (println "No fit file found for" (:pattern opts))))
          (println "Invalid metric. Available: hr, ef, pace, cadence, step-length")))))

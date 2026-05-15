(ns strava.cli.line
  (:require [babashka.cli :as cli]
            [strava.analysis :as analysis]
            [strava.repo :as repo]
            [strava.track :as track]))

(def metrics {:hr {:key :heart_rate :label "HR (bpm)"}
              :ef {:key :ef_metric :label "EF"}
              :pace {:key :pace :label "Pace (s/km)"}
              :cadence {:key :cadence :label "Cadence (rpm)"}
              :step-length {:key :step_length :label "Step length (cm)"}})

(def spec {:pattern {:alias :p :coerce [] :require true}
           :metric {:default "pace" :desc "Metric to plot: hr, ef, pace, cadence, step-length"}
           :interval {:alias :i :coerce analysis/parse-at :default 60}
           :from {:coerce analysis/parse-at}
           :to {:coerce analysis/parse-at}
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

(defn format-axis-value [key val]
  (if (= key :pace)
    (format "%dm%ds" (int (quot val 60)) (int (mod val 60)))
    (format "%.0f" (double val))))

(defn compute-stats [values]
  (when (seq values)
    (let [sorted (sort values)
          n (count sorted)
          mean (/ (reduce + values) n)
          median (if (even? n)
                   (/ (+ (nth sorted (dec (/ n 2))) (nth sorted (/ n 2))) 2.0)
                   (nth sorted (/ n 2)))
          variance (/ (reduce + (map #(* (- % mean) (- % mean)) values)) n)
          sd (Math/sqrt variance)]
      {:mean mean :median median :sd sd :count n})))

(defn parse-clip-value [metric-key clip-val]
  (when clip-val
    (if (= metric-key :pace)
      (if (string? clip-val)
        (analysis/parse-at clip-val)
        clip-val)
      (if (string? clip-val)
        (Double/parseDouble clip-val)
        (double clip-val)))))

(defn apply-clipping [values metric-key clip-min clip-max]
  (let [min-val (parse-clip-value metric-key clip-min)
        max-val (parse-clip-value metric-key clip-max)]
    (cond-> values
      min-val (as-> v (map #(max % min-val) v))
      max-val (as-> v (map #(min % max-val) v)))))

(defn count-clipped [values metric-key clip-min clip-max]
  (let [min-val (parse-clip-value metric-key clip-min)
        max-val (parse-clip-value metric-key clip-max)]
    {:below-min (when min-val (count (filter #(< % min-val) values)))
     :above-max (when max-val (count (filter #(> % max-val) values)))}))

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
          (let [val (+ min-val (/ (* (- (dec h) row) val-range) (dec h)))]
            (print (format "%8s |" (format-axis-value metric-key val))))
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

(defn activity-label [f]
  (if-let [activity (some-> (repo/extract-id f) repo/find-activity)]
    (let [date (some-> (:start_date_local activity) (subs 0 10))]
      (format "%s %s %.0fkm" date (:name activity) (/ (:distance activity) 1000.0)))
    (str f)))

(defn parse-file [f]
  (-> (track/parse-file f) track/add-duration track/remove-head track/remove-tail))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            metric (keyword (:metric opts))
            metric-config (get metrics metric)]
        (if metric-config
          (let [files (->> (mapcat #(repo/find-by-pattern %) (:pattern opts))
                           distinct
                           (sort-by str #(compare %2 %1))
                           (take 1))]
            (if (seq files)
              (let [f (first files)
                    records (parse-file f)
                    bucketed (analysis/select-data opts records)
                    metric-key (:key metric-config)
                    data-with-time (map #(vector (:at %) (metric-key %)) bucketed)
                    values (map second data-with-time)
                    times (map first data-with-time)
                    sd-clip-vals (when (:sd-clip opts)
                                   (when-let [{:keys [mean sd]} (compute-stats values)]
                                     {:clip-min (- mean (* (:sd-clip opts) sd))
                                      :clip-max (+ mean (* (:sd-clip opts) sd))}))
                    clip-min-final (or (:clip-min sd-clip-vals) (:clip-min opts))
                    clip-max-final (or (:clip-max sd-clip-vals) (:clip-max opts))
                    clipped-values (apply-clipping values metric-key clip-min-final clip-max-final)
                    clip-info (count-clipped values metric-key clip-min-final clip-max-final)]
                (println (activity-label f))
                (when (or (:below-min clip-info) (:above-max clip-info))
                  (println)
                  (when (:below-min clip-info)
                    (println (format "  (clipped %d values below %s)"
                                     (:below-min clip-info)
                                     (format-axis-value metric-key (parse-clip-value metric-key clip-min-final)))))
                  (when (:above-max clip-info)
                    (println (format "  (clipped %d values above %s)"
                                     (:above-max clip-info)
                                     (format-axis-value metric-key (parse-clip-value metric-key clip-max-final))))))
                (when (seq clipped-values)
                  (line-plot opts metric-key (:label metric-config) clipped-values times)))
              (println "No fit file found for" (:pattern opts))))
          (println "Invalid metric. Available: hr, ef, pace, cadence, step-length")))))

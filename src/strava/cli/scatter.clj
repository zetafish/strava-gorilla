(ns strava.cli.scatter
  (:require [babashka.cli :as cli]
            [strava.analysis :as analysis]
            [strava.repo :as repo]
            [strava.track :as track]))

(def markers [\● \○ \× \△ \◆ \□ \▲ \◇ \★ \▽])

(def axes {:hr {:key :heart_rate :label "HR (bpm)"}
           :ef {:key :ef_metric :label "EF"}
           :pace {:key :pace :label "Pace (s/km)"}
           :cadence {:key :cadence :label "Cadence (rpm)"}
           :step-length {:key :step_length :label "Step length (cm)"}})

(def spec {:pattern {:alias :p :coerce [] :require true}
           :interval {:alias :i :coerce analysis/parse-at :default 60}
           :from {:coerce analysis/parse-at}
           :to {:coerce analysis/parse-at}
           :width {:coerce :long :default 60}
           :height {:coerce :long :default 20}
           :n {:coerce :long :default 10 :desc "Max number of activities"}
           :x {:default "hr" :desc "X axis: hr, ef, pace, cadence, step-length"}
           :y {:default "ef" :desc "Y axis: hr, ef, pace, cadence, step-length"}
           :no-table {:type :boolean :desc "Omit the data table"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb scatter -p <pattern> [-i interval] [--from FROM] [--to TO] [-n N] [-x hr] [-y ef]")
    (println)
    (println "Available axes: hr, ef, pace, cadence, step-length")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn format-axis-value [key val]
  (if (= key :pace)
    (format "%dm%ds" (int (quot val 60)) (int (mod val 60)))
    (format "%.0f" (double val))))

(defn scatter-plot [{:keys [width height]} series x-key y-key x-label y-label]
  (let [all-points (mapcat :points series)
        filtered (filter #(and (x-key %) (y-key %)) all-points)
        xs (keep x-key filtered)
        ys (keep y-key filtered)
        x-min (apply min xs)
        x-max (apply max xs)
        y-min (apply min ys)
        y-max (apply max ys)
        x-range (max 1.0 (- x-max x-min))
        y-range (max 0.01 (- y-max y-min))
        grid (vec (repeat height (vec (repeat width \space))))
        grid (reduce
              (fn [g {:keys [marker points]}]
                (reduce
                 (fn [g pt]
                   (let [x (x-key pt)
                         y (y-key pt)]
                     (if (and x y)
                       (let [col (min (dec width) (int (* (/ (- x x-min) x-range) (dec width))))
                             row (min (dec height) (int (* (/ (- y y-min) y-range) (dec height))))
                             row (- (dec height) row)]
                         (assoc-in g [row col] marker))
                       g)))
                 g
                 points))
              grid
              series)
        y-label-w 8]
    (println)
    (println (format (str "%" y-label-w "s  %s") y-label x-label))
    (println (apply str (repeat (+ y-label-w 2 width) \-)))
    (doseq [r (range height)]
      (let [y-val (+ y-min (* y-range (/ (- (dec height) r) (dec height))))]
        (print (format (str "%" y-label-w "s") (format-axis-value y-key y-val)))
        (print " |")
        (doseq [c (range width)]
          (print (get-in grid [r c])))
        (println)))
    (println (str (apply str (repeat y-label-w \space))
                  " +"
                  (apply str (repeat width \-))))
    (let [x-min-s (format-axis-value x-key x-min)
          x-max-s (format-axis-value x-key x-max)]
      (println (str (apply str (repeat (+ y-label-w 2) \space))
                    x-min-s
                    (apply str (repeat (- width (count x-min-s) (count x-max-s)) \space))
                    x-max-s)))
    (when (> (count series) 1)
      (println)
      (doseq [{:keys [marker label]} series]
        (println (format "  %s  %s" marker label))))))

(defn print-table [points]
  (let [fmt "%6s %8s %8s %8s"]
    (println (apply format fmt ["HR" "EF" "Pace" "km/h"]))
    (println (apply str (repeat 35 \-)))
    (doseq [pt (sort-by :heart_rate points)]
      (when (and (:heart_rate pt) (:ef_metric pt))
        (println (format fmt
                         (:heart_rate pt)
                         (some-> (:ef_metric pt) (as-> v (format "%.3f" v)))
                         (some-> (:pace pt) (as-> v (format "%d:%02d" (quot v 60) (mod v 60))))
                         (some-> (:kmph pt) (as-> v (format "%.1f" v)))))))))

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
            x-axis (keyword (:x opts))
            y-axis (keyword (:y opts))
            x-config (get axes x-axis)
            y-config (get axes y-axis)]
        (if (and x-config y-config)
          (let [files (->> (mapcat #(repo/find-by-pattern %) (:pattern opts))
                           distinct
                           (sort-by str #(compare %2 %1))
                           (take (:n opts)))]
            (if (seq files)
              (let [series (map-indexed
                            (fn [i f]
                              {:marker (nth markers (mod i (count markers)))
                               :label (activity-label f)
                               :points (analysis/select-data opts (parse-file f))})
                            files)]
                (doseq [{:keys [label points marker]} series]
                  (println (format "%s %s (%d pts)" marker label (count points))))
                (scatter-plot opts series (:key x-config) (:key y-config) (:label x-config) (:label y-config))
                (when (and (= 1 (count series)) (not (:no-table opts)))
                  (println)
                  (print-table (:points (first series)))))
              (println "No fit file found for" (:pattern opts))))
          (println "Invalid axes. Available: hr, ef, pace, cadence, step-length")))))

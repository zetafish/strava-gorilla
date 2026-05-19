(ns strava.cli.load
  (:require [babashka.cli :as cli]
            [cheshire.core :as json]
            [clojure.string :as str]
            [strava.load :as load]
            [strava.tags :as tags]))

(def spec {:weeks {:alias :w :coerce :long :default 12 :desc "Number of weeks (ignored if --from used)"}
           :from {:desc "Start date (YYYY-MM-DD)"}
           :to {:desc "End date (YYYY-MM-DD, default today)"}
           :resting-hr {:coerce :long :desc "Override resting HR"}
           :max-hr {:coerce :long :desc "Override max HR"}
           :format {:alias :f :default "table" :validate #{"table" "csv" "json"}}})

(defn help-requested [args]
  (when (or (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb load [--weeks 12] [--from DATE] [--to DATE] [--format table|csv|json]")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn status-label
  ([atl ctl] (status-label atl ctl nil))
  ([atl ctl prev-ac]
   (let [ac (if (pos? ctl) (* 100.0 (/ atl ctl)) 0)]
     (cond
       (>= ac 150) "Excessive"
       (>= ac 100) "Optimized"
       (>= ac 80) "Maintaining"
       (>= ac 50) (if (and prev-ac (< ac prev-ac)) "Performance" "Resuming")
       :else "Decreasing"))))

(defn sample-data [data w]
  (let [n (count data)]
    (if (<= n w)
      (vec data)
      (let [indices (mapv #(int (* (/ % (dec w)) (dec n))) (range w))]
        (mapv #(nth data %) (distinct indices))))))

(defn dual-line-chart [{:keys [width height]} data]
  (when (seq data)
    (let [sampled (sample-data data width)
          w (count sampled)
          h height
          all-vals (concat (map :atl sampled) (map :ctl sampled))
          max-val (max 1.0 (apply max all-vals))
          min-val (min 0.0 (apply min (map :tsb sampled)))
          val-range (max 1.0 (- max-val min-val))
          y-of (fn [v] (int (* (dec h) (/ (- max-val v) val-range))))
          canvas (vec (repeat h (vec (repeat w \space))))]

      (println)
      (println "  ATL ─  CTL ━  TSB ┄")
      (println)

      (let [painted
            (reduce
             (fn [c [i day]]
               (let [ya (y-of (:atl day))
                     yc (y-of (:ctl day))
                     yt (y-of (:tsb day))
                     ya (max 0 (min (dec h) ya))
                     yc (max 0 (min (dec h) yc))
                     yt (max 0 (min (dec h) yt))]
                 (-> c
                     (assoc-in [yt i] \┄)
                     (assoc-in [yc i] \━)
                     (assoc-in [ya i] \─))))
             canvas
             (map-indexed vector sampled))]

        (doseq [row (range h)]
          (let [v (- max-val (* row (/ val-range (dec h))))]
            (print (format "%6.0f│" (double v))))
          (doseq [col (range w)]
            (print (get-in painted [row col] \space)))
          (println)))

      (println (str "      └" (apply str (repeat w \─))))
      (let [dates (map :date sampled)
            first-d (subs (first dates) 5)
            last-d (subs (last dates) 5)
            mid-i (quot (count dates) 2)
            mid-d (subs (nth dates mid-i) 5)
            left-pad (- (quot w 2) (count first-d))
            right-pad (- w (quot w 2) (count mid-d) (count last-d))]
        (println (str "       " first-d
                      (apply str (repeat (max 1 left-pad) \space))
                      mid-d
                      (apply str (repeat (max 1 right-pad) \space))
                      last-d))))))

(defn week-of [date-str]
  (let [d (java.time.LocalDate/parse date-str)]
    (str (.with d (java.time.temporal.TemporalAdjusters/previousOrSame java.time.DayOfWeek/MONDAY)))))

(defn summarize-week [days]
  (let [last-day (last days)]
    {:date (:date (first days))
     :trimp (reduce + (map :trimp days))
     :distance (reduce + (map :distance days))
     :atl (:atl last-day)
     :ctl (:ctl last-day)
     :tsb (:tsb last-day)}))

(defn print-table [data athlete]
  (let [days (count data)
        weekly? (> days 28)
        rows (if weekly?
               (->> data
                    (group-by #(week-of (:date %)))
                    (sort-by key)
                    (map (fn [[_ days]] (summarize-week days))))
               data)
        sep (apply str (repeat 75 \-))
        label (if weekly? "Week" "Date")
        intensity (fn [d] (if (pos? (:ctl d)) (/ (:atl d) (:ctl d)) 0))]
    (println)
    (println sep)
    (println (format "%-12s %6s %6s %5s %5s %6s %6s  %-15s" label "TRIMP" "km" "ATL" "CTL" "TSB" "A/C" "Status"))
    (println sep)
    (let [row-pairs (map vector (cons nil rows) rows)]
      (doseq [[prev d] row-pairs]
        (println (format "%-12s %6.0f %6.1f %5.0f %5.0f %+6.0f %5.0f%%  %-15s"
                         (:date d)
                         (double (:trimp d))
                         (double (:distance d))
                         (double (:atl d))
                         (double (:ctl d))
                         (double (:tsb d))
                         (* 100.0 (double (intensity d)))
                         (status-label (:atl d) (:ctl d)
                                       (when prev (* 100.0 (double (intensity prev)))))))))
    (println sep)
    (let [today (last data)
          prev (last (butlast data))]
      (println (format "  Current: ATL %.0f  CTL %.0f  TSB %+.0f  A/C %.0f%% (%s)"
                       (double (:atl today))
                       (double (:ctl today))
                       (double (:tsb today))
                       (* 100.0 (double (intensity today)))
                       (status-label (:atl today) (:ctl today)
                                     (when prev (* 100.0 (double (intensity prev)))))))
      (println (format "  Config:  resting-hr %d  max-hr %d"
                       (:resting-hr athlete)
                       (:max-hr athlete))))))

(defn print-csv [data]
  (println "date,trimp,distance,atl,ctl,tsb")
  (doseq [d data]
    (println (format "%s,%.1f,%.1f,%.1f,%.1f,%.1f"
                     (:date d)
                     (double (:trimp d))
                     (double (:distance d))
                     (double (:atl d))
                     (double (:ctl d))
                     (double (:tsb d))))))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            athlete (cond-> (load/load-athlete)
                      (:resting-hr opts) (assoc :resting-hr (:resting-hr opts))
                      (:max-hr opts) (assoc :max-hr (:max-hr opts)))
            activities (tags/load-all-activities)
            data (load/compute-load activities athlete opts)]
        (if (seq data)
          (do
            (let [period (if (:from opts)
                           (str (:from opts) " to " (or (:to opts) "today"))
                           (str (:weeks opts) " weeks"))]
              (println (format "Training Load (%s)  resting-hr: %d  max-hr: %d"
                               period (:resting-hr athlete) (:max-hr athlete))))
            (case (:format opts)
              "table" (do (dual-line-chart {:width 60 :height 15} data)
                          (print-table data athlete))
              "csv" (print-csv data)
              "json" (println (json/encode data {:pretty true}))))
          (println "No run activities with HR data found.")))))

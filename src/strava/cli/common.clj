(ns strava.cli.common
  (:require [strava.analysis :as analysis]
            [strava.repo :as repo]
            [strava.track :as track]))

(def metrics {:hr {:key :heart_rate :label "HR (bpm)"}
              :ef {:key :ef_metric :label "EF"}
              :pace {:key :pace :label "Pace (s/km)"}
              :cadence {:key :cadence :label "Cadence (rpm)"}
              :step-length {:key :step_length :label "Step length (cm)"}})

(defn format-axis-value [key val]
  (cond
    (= key :pace) (format "%dm%ds" (int (quot val 60)) (int (mod val 60)))
    (= key :ef_metric) (format "%.3f" (double val))
    :else (format "%.0f" (double val))))

(defn parse-clip-value [metric-key clip-val]
  (when clip-val
    (if (= metric-key :pace)
      (if (string? clip-val)
        (analysis/parse-at clip-val)
        clip-val)
      (if (string? clip-val)
        (Double/parseDouble clip-val)
        (double clip-val)))))

(defn activity-label [f]
  (if-let [activity (some-> (repo/extract-id f) repo/find-activity)]
    (let [date (some-> (:start_date_local activity) (subs 0 10))]
      (format "%s %s %.0fkm" date (:name activity) (/ (:distance activity) 1000.0)))
    (str f)))

(defn parse-file [f]
  (-> (track/parse-file f) track/add-duration track/remove-head track/remove-tail))

(defn prepare-line-data
  [f opts metric-config]
  (let [records (parse-file f)
        bucketed (analysis/select-data opts records)
        metric-key (:key metric-config)
        data-with-time (keep #(when-let [v (metric-key %)] [(:at %) v]) bucketed)
        values (map second data-with-time)
        times (map first data-with-time)
        sd-clip-vals (when (:sd-clip opts)
                       (when-let [{:keys [mean sd]} (analysis/compute-stats values)]
                         {:clip-min (- mean (* (:sd-clip opts) sd))
                          :clip-max (+ mean (* (:sd-clip opts) sd))}))
        clip-min-final (or (:clip-min sd-clip-vals) (parse-clip-value metric-key (:clip-min opts)))
        clip-max-final (or (:clip-max sd-clip-vals) (parse-clip-value metric-key (:clip-max opts)))
        clipped-values (analysis/clip-values values clip-min-final clip-max-final)
        clip-info (analysis/count-clipped values clip-min-final clip-max-final)]
    {:values clipped-values
     :times times
     :clip-info clip-info
     :clip-min clip-min-final
     :clip-max clip-max-final
     :metric-key metric-key
     :metric-label (:label metric-config)}))

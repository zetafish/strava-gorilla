(ns strava.notebook
  {:nextjournal.clerk/visibility {:code :hide}}
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]
            [strava.analysis :as analysis]
            [strava.cli.common :as common]
            [strava.repo :as repo]))

^{::clerk/visibility {:result :hide}}
(def opts {:interval 60})

^{::clerk/visibility {:result :hide}}
(defn make-select-viewer [options]
  (assoc viewer/render-eval-viewer
         :render-fn (list 'fn '[!state]
                          (into
                           [:select {:value '@!state
                                     :class "px-3 py-2 bg-white rounded text-sm border border-gray-300 outline-none focus:ring w-full"
                                     :on-change '(fn [e] (reset! !state (.. e -target -value)))}]
                           (mapv (fn [[v label]] [:option {:value v} label]) options)))))

;; ## Month
^{::clerk/visibility {:result :hide}}
(def month-options
  (->> (fs/list-dir ".activities" "*.json")
       (map #(-> % fs/file-name (str/replace ".json" "")))
       sort
       reverse
       (mapv (fn [m] [m m]))))

^{::clerk/sync true ::clerk/viewer (make-select-viewer month-options)}
(defonce month-select (atom (ffirst month-options)))

;; ## Activity
^{::clerk/visibility {:result :hide}}
(def activities
  (json/parse-string (slurp (str ".activities/" @month-select ".json")) true))

^{::clerk/visibility {:result :hide}}
(def activity-options
  (mapv (fn [a]
          [(str (:id a))
           (format "%s %s %.1fkm"
                   (subs (:start_date_local a) 0 10)
                   (:name a)
                   (/ (:distance a) 1000.0))])
        activities))

^{::clerk/visibility {:result :hide}}
(def activity-ids (set (map first activity-options)))

^{::clerk/sync true ::clerk/viewer (make-select-viewer activity-options)}
(defonce activity-select (atom nil))

^{::clerk/visibility {:result :hide}}
(when-not (activity-ids @activity-select)
  (reset! activity-select (ffirst activity-options)))

^{::clerk/visibility {:result :show}}
(def activity-id-param (some-> @activity-select parse-long))

^{::clerk/visibility {:result :hide}}
(def fit-file
  (when activity-id-param
    (first (repo/find-by-pattern (str activity-id-param)))))

^{::clerk/visibility {:result :hide}}
(def label (when fit-file (common/activity-label fit-file)))

(when label (clerk/md (str "**" label "**")))

^{::clerk/visibility {:result :hide}}
(def records (when fit-file (common/parse-file fit-file)))

^{::clerk/visibility {:result :hide}}
(def bucketed-raw (when records (analysis/select-data opts records)))

;; ## Outlier SD
^{::clerk/sync true ::clerk/viewer (make-select-viewer [["0" "Off"]
                                                        ["5" "Loose"]
                                                        ["4" "Normal"]
                                                        ["3" "Tight"]])}
(defonce sd-select (atom "0"))

^{::clerk/visibility {:result :hide}}
(defn mad-filter [values n]
  (let [sorted (sort values)
        median (nth sorted (/ (count sorted) 2))
        mad (nth (sort (map #(Math/abs (- % median)) sorted)) (/ (count sorted) 2))
        lower (- median (* n mad))
        upper (+ median (* n mad))]
    {:lower lower :upper upper}))

^{::clerk/visibility {:result :hide}}
(def bucketed
  (when bucketed-raw
    (let [sd (parse-double @sd-select)]
      (if (pos? sd)
        (let [paces (keep :pace bucketed-raw)
              {:keys [lower upper]} (mad-filter paces sd)]
          (filter #(if-let [p (:pace %)] (and (>= p lower) (<= p upper)) true) bucketed-raw))
        bucketed-raw))))

;; ## Heart Rate
(when bucketed
  (clerk/vl
   {:width 600
    :data {:values bucketed}
    :mark {:type "point"}
    :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
               :y {:field "heart_rate" :type "quantitative" :title "HR (bpm)"
                   :scale {:zero false}}
               :toolip [{:field :heart_rate}]
               :color {:value "#e74c3c"}}}))

;; ## Pace
(when bucketed
  (let [fmt (fn [s] (format "%dm%ds" (int (quot s 60)) (int (mod s 60))))
        data (keep #(when-let [p (:pace %)] {:at (:at %) :pace p :pace_fmt (fmt p)}) bucketed)]
    (when (seq data)
      (clerk/vl
       {:width 600
        :data {:values data}
        :mark {:type "point"}
        :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
                   :y {:field "pace" :type "quantitative" :title "Pace (min/km)"
                       :scale {:zero false}
                       :axis {:labelExpr "floor(datum.value / 60) + 'm' + floor(datum.value % 60) + 's'"}}
                   :color {:value "#1abc9c"}
                   :tooltip [{:field "pace_fmt" :type "nominal" :title "Pace"}]}}))))

;; ## Pace Distribution
^{::clerk/visibility {:result :hide}}
(def pace-buckets
  [[180 240 "3-4m (5k)"]
   [240 300 "4-5m (HM)"]
   [300 360 "5-6m (steady)"]
   [360 420 "6-7m (easy)"]
   [420 480 "7-8m (slow)"]
   [480 600 "8-10m (shuffle)"]
   [600 720 "10-12m (walk)"]
   [720 1200 "12-20m (walk slow)"]])

(when bucketed
  (let [paces (keep :pace bucketed)
        counts (mapv (fn [[lo hi label]]
                       {:bucket label
                        :count (count (filter #(and (>= % lo) (< % hi)) paces))
                        :sort (- lo)})
                     pace-buckets)]
    (clerk/vl
     {:width 600
      :data {:values (filter #(pos? (:count %)) counts)}
      :mark {:type "bar" :color "#1abc9c"}
      :encoding {:y {:field "bucket" :type "nominal" :title "Pace"
                     :sort {:field "sort"}}
                 :x {:field "count" :type "quantitative" :title "Minutes"}}})))

;; ## Efficiency Factor
(when bucketed
  (clerk/vl
   {:width 600
    :data {:values bucketed}
    :mark {:type "point"}
    :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
               :y {:field "ef_metric" :type "quantitative" :title "EF (metric)"
                   :scale {:zero false}}
               :tooltip [{:field :ef_metric}]
               :color {:value "#e67e22"}}}))

;; ## EF vs Heart Rate
(when bucketed
  (clerk/vl
   {:width 600
    :data {:values bucketed}
    :mark {:type "point" :filled true :opacity 0.6 :size 30}
    :encoding {:x {:field "heart_rate" :type "quantitative" :title "HR (bpm)"}
               :y {:field "ef_metric" :type "quantitative" :title "EF (metric)"
                   :scale {:zero false}}
               :tooltip [{:field :ef_metric} {:field :heart_rate}]
               :color {:field "at" :type "quantitative" :scale {:scheme "viridis"}
                       :legend {:title "Time (s)"}}}}))

;; ## Cadence
(when bucketed
  (clerk/vl
   {:width 600
    :data {:values (filter :cadence bucketed)}
    :mark {:type "point"}
    :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
               :y {:field "cadence" :type "quantitative" :title "Cadence (rpm)"}
               :tooltip [{:field :cadence}]
               :color {:value "#9b59b6"}}}))

;; ## Step Length
(when bucketed
  (clerk/vl
   {:width 600
    :data {:values (filter :step_length bucketed)}
    :mark {:type "point"}
    :encoding {:x {:field "at" :type "quantitative" :title "Time (s)"}
               :y {:field "step_length" :type "quantitative" :title "Step length (cm)"}
               :tooltip [{:field :step_length}]
               :color {:value "#9b59b6"}}}))

;; ## Route Map
(when records
  (clerk/vl
   {:data {:values (keep #(when (and (:position_lat %) (:position_long %))
                            {:lat (:position_lat %) :lng (:position_long %)})
                         records)}
    :mark {:type "line" :stroke "#3498db" :strokeWidth 2}
    :encoding {:latitude {:field "lat" :type "quantitative"}
               :longitude {:field "lng" :type "quantitative"}}}))

;; ## Stats
(when bucketed
  (let [stats-for (fn [k label]
                    (when-let [vals (seq (keep k bucketed))]
                      (let [{:keys [mean median sd count]} (analysis/compute-stats vals)]
                        [label count
                         (common/format-axis-value k (apply min vals))
                         (common/format-axis-value k (apply max vals))
                         (common/format-axis-value k mean)
                         (common/format-axis-value k median)
                         (common/format-axis-value k sd)])))]
    (clerk/table
     {:head ["Metric" "N" "Min" "Max" "Mean" "Median" "SD"]
      :rows (keep identity
                  [(stats-for :heart_rate "HR (bpm)")
                   (stats-for :pace "Pace (s/km)")
                   (stats-for :ef_metric "EF")
                   (stats-for :cadence "Cadence (rpm)")
                   (stats-for :step_length "Step length (cm)")])})))

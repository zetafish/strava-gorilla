(ns strava.analysis
  (:require [clojure.string :as str]))

(def units {:speed "m/s"
            :kmph "km/h"
            :pace "s/km"
            :distance "m"
            :duration "s"})

(defn avg [k coll]
  (let [vals (keep k coll)]
    (when (seq vals)
      (double (/ (reduce + vals) (count vals))))))

(defn efficiency [{:keys [speed heart_rate]}]
  (when (and speed heart_rate (pos? heart_rate))
    (/ speed heart_rate)))

(defn pace [{:keys [speed]}]
  (when (and speed (pos? speed))
    (int (/ 3600 (* 3.6 speed)))))

(defn kmph [{:keys [speed]}]
  (when speed
    (* 3.6 speed)))

(defn enrich [m]
  (cond-> (assoc m :pace (pace m) :kmph (some-> (:speed m) (* 3.6)))
    (efficiency m) (assoc :ef_si (efficiency m)
                          :ef_metric (* 60 (efficiency m)))))

(defn bucket-fn [ts start-ts window]
  (* window (quot (- ts start-ts) window)))

(defn agg [coll]
  (case (count coll)
    0 nil
    1 (enrich (assoc (first coll) :pts 1))
    (enrich {:at (:at (first coll))
             :timestamp (:timestamp (first coll))
             :heart_rate (some-> (avg :heart_rate coll) int)
             :cadence (some-> (avg :cadence coll) int)
             :step_length (some-> (avg :step_length coll) int)
             :distance (:distance (last coll))
             :duration (reduce + (keep :duration coll))
             :speed (/ (- (:distance (last coll)) (:distance (first coll)))
                       (- (:timestamp (last coll)) (:timestamp (first coll))))
             :pts (count coll)})))

(defn bucketize [window records]
  (let [start-ts (:timestamp (first records))]
    (->> (group-by #(bucket-fn (:timestamp %) start-ts window) records)
         vals
         (sort-by (comp :timestamp first))
         (map agg)
         (remove nil?))))

(defn parse-at [s]
  (when s
    (let [s (str/trim s)]
      (cond
        (re-matches #"\d+h\d+m?" s)
        (let [[_ h m] (re-matches #"(\d+)h(\d+)m?" s)]
          (+ (* (parse-long h) 3600) (* (parse-long m) 60)))

        (re-matches #"\d+h" s)
        (* (parse-long (str/replace s "h" "")) 3600)

        (re-matches #"\d+m" s)
        (* (parse-long (str/replace s "m" "")) 60)

        (re-matches #"\d+s" s)
        (parse-long (str/replace s "s" ""))))))

(defn select-data [{:keys [from to interval]} records]
  (cond->> records
    from (drop-while #(< (:at %) from))
    to (take-while #(< (:at %) to))
    true (bucketize interval)))

(defn trend-summary [records hr-min hr-max]
  (let [enriched (map enrich records)
        in-band (filter #(and (:heart_rate %)
                              (<= hr-min (:heart_rate %) hr-max))
                        enriched)
        all-hr (keep :heart_rate enriched)
        all-ef (keep :ef_metric enriched)
        band-ef (keep :ef_metric in-band)]
    (when (seq all-hr)
      {:avg-hr (int (/ (reduce + all-hr) (count all-hr)))
       :avg-pace (when-let [spd (avg :speed enriched)]
                   (when (pos? spd)
                     (int (/ 3600 (* 3.6 spd)))))
       :ef-band (when (seq band-ef)
                  (/ (reduce + band-ef) (count band-ef)))
       :band-pts (count band-ef)
       :avg-ef (when (seq all-ef)
                 (/ (reduce + all-ef) (count all-ef)))})))

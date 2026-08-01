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
    (:cadence m) (update :cadence * 2)
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
             :duration (inc (- (:timestamp (last coll)) (:timestamp (first coll))))
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

(defn bucketize-groups
  "Like `bucketize` but returns the raw sample groups instead of aggregating.
  Each group is a seq of records falling into one bucket, ordered by time."
  [window records]
  (let [start-ts (:timestamp (first records))]
    (->> (group-by #(bucket-fn (:timestamp %) start-ts window) records)
         vals
         (sort-by (comp :timestamp first)))))

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
      {:heart_rate (int (/ (reduce + all-hr) (count all-hr)))
       :pace (when-let [spd (avg :speed enriched)]
               (when (pos? spd)
                 (int (/ 3600 (* 3.6 spd)))))
       :ef (when (seq band-ef)
             (/ (reduce + band-ef) (count band-ef)))
       :pts (count band-ef)
       :avg-ef (when (seq all-ef)
                 (/ (reduce + all-ef) (count all-ef)))})))

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

(defn detect-outliers [values sd-threshold]
  (when-let [{:keys [mean sd]} (compute-stats values)]
    (let [lower (- mean (* sd-threshold sd))
          upper (+ mean (* sd-threshold sd))]
      {:lower lower :upper upper
       :outliers (filter #(or (< % lower) (> % upper)) values)})))

(defn clip-values [values min-val max-val]
  (cond-> values
    min-val (as-> v (map #(max % min-val) v))
    max-val (as-> v (map #(min % max-val) v))))

(defn count-clipped [values min-val max-val]
  {:below-min (when min-val (count (filter #(< % min-val) values)))
   :above-max (when max-val (count (filter #(> % max-val) values)))})

(defn baseline-ef [activities window-days decay ref-date]
  (let [ref (java.time.LocalDate/parse ref-date)
        start (.minusDays ref window-days)
        eligible (->> activities
                      (filter #(= "Run" (:sport_type %)))
                      (filter :average_speed)
                      (filter :average_heartrate)
                      (filter #(pos? (:average_heartrate %)))
                      (keep (fn [a]
                              (when-let [d (some-> (:start_date a) (subs 0 10))]
                                (let [ld (java.time.LocalDate/parse d)]
                                  (when (and (not (.isBefore ld start))
                                             (.isBefore ld ref))
                                    (let [days-ago (.until ld ref java.time.temporal.ChronoUnit/DAYS)
                                          w (Math/pow decay days-ago)
                                          e (* 60.0 (/ (:average_speed a) (:average_heartrate a)))]
                                      {:ef e :weight w})))))))]
    (when (seq eligible)
      (let [total-w (reduce + (map :weight eligible))
            weighted-sum (reduce + (map #(* (:ef %) (:weight %)) eligible))]
        (/ weighted-sum total-w)))))

(defn cardiac-drift [records]
  (let [n (count records)
        third (quot n 3)]
    (when (>= third 10)
      (let [first-hrs (keep :heart_rate (take third records))
            last-hrs (keep :heart_rate (drop (* 2 third) records))]
        (when (and (seq first-hrs) (seq last-hrs))
          (let [avg-first (/ (reduce + first-hrs) (double (count first-hrs)))
                avg-last (/ (reduce + last-hrs) (double (count last-hrs)))]
            (* 100.0 (/ (- avg-last avg-first) avg-first))))))))

(defn positive-split [records]
  (let [n (count records)
        half (quot n 2)]
    (when (>= half 10)
      (let [first-half (take half records)
            second-half (drop half records)
            avg-pace (fn [recs]
                       (let [paces (keep #(when (pos? (:speed % 0)) (pace %)) recs)]
                         (when (seq paces)
                           (/ (reduce + paces) (double (count paces))))))]
        (when-let [p1 (avg-pace first-half)]
          (when-let [p2 (avg-pace second-half)]
            (/ p2 p1)))))))

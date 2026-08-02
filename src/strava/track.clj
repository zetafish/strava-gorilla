(ns strava.track
  (:require [strava.util :refer [with-derived-metrics avg]]))

;; https://apizone.suunto.com/fit-description

(defn gait
  "Classify a sample as :run, :walk, or :idle.
  Requires speed >= `run-speed` (m/s). When cadence is present it must also
  reach `run-cadence` (steps/min, spm — the raw cadence field is doubled).
  Missing cadence (common at the start of a run) falls back to speed alone.
  Anything moving but below thresholds is :walk. Non-moving is :idle."
  ([sample] (gait sample 2 150))
  ([{:keys [speed cadence]} run-speed run-cadence]
   (cond
     (or (nil? speed) (zero? speed)) :idle
     (and cadence (>= (* 2 cadence) run-cadence)) :run
     (>= speed run-speed) :run
     :else :walk)))

(defn add-gait [track]
  (map #(assoc % :gait (gait %)) track))

(defn stats [coll]
  (when (seq coll)
    (let [coll (add-gait coll)
          active? (fn [[a b]] (and (= 1 (- (:at b) (:at a)))
                                   (#{:run :walk} (:gait a))
                                   (#{:run :walk} (:gait b))))
          elapsed (- (:at (last coll)) (:at (first coll)))
          active-samples (filter (comp #{:run :walk} :gait) coll)
          active-pairs (->> coll
                            (partition 2 1)
                            (filter active?))
          moving (reduce + (map (fn [[a b]] (- (:at b) (:at a)))
                                active-pairs))
          covered (reduce + (map (fn [[a b]] (- (:distance b) (:distance a)))
                                 active-pairs))]
      {:sample-count (count coll)
       :active-sample-count (count active-samples)
       :timestamp (:timestamp (first coll))
       :from (:at (first coll))
       :to (:at (last coll))
       :elapsed elapsed
       :moving moving
       :covered covered
       :distance (:distance (last coll))
       :cadence (avg active-samples :cadence)
       :step-length (avg active-samples :step-length)
       :heart-rate (avg active-samples :heart-rate)})))

(defn agg [coll & {:keys [mode]}]
  (when (seq coll)
    (let [{:keys [moving elapsed covered] :as m} (stats coll)
          speed (when covered
                  (if (= :race mode)
                    (when (pos? elapsed) (/ covered elapsed))
                    (when (pos? moving) (/ covered moving))))]
      (-> m
          (assoc :speed speed)
          with-derived-metrics))))

(defn- make-boundary [at prev fallback-ts]
  {:synthetic :boundary
   :at at
   :timestamp (if prev
                (+ (:timestamp prev) (- at (:at prev)))
                (+ at fallback-ts))
   :distance (some-> prev :distance)})

(defn- emit-boundaries
  "Emits boundaries starting from `b-at` up to (but not including) `limit`."
  [acc b-at limit prev fallback-ts window]
  (loop [b b-at acc acc]
    (if (< b limit)
      (recur (+ b window)
             (conj acc (make-boundary b prev fallback-ts)))
      [acc b])))

(defn insert-boundaries
  "Inserts synthetic boundary maps into a track at fixed window intervals."
  [window track]
  (when-let [t0 (:at (first track))]
    (let [t-start (:timestamp (first track))
          ;; Step 1: Reduce track to insert boundaries before/between samples
          [acc b-at prev]
          (reduce
           (fn [[acc b-at prev] s]
             (let [s-at (:at s)
                   ;; Catch up boundaries prior to current sample
                   [acc b-at] (emit-boundaries acc b-at s-at prev t-start window)
                   ;; If real sample lands on boundary, advance boundary target
                   b-at (if (= s-at b-at) (+ b-at window) b-at)]
               [(conj acc s) b-at s]))
           [[] t0 nil]
           track)]

      ;; Step 2: Emit remaining trailing boundary at tn if it wasn't hit
      (first (emit-boundaries acc b-at (:at prev) prev t-start window)))))

(defn split-evenly [n coll]
  (let [v (vec coll)
        c (count v)
        q (quot c n)
        r (rem c n)
        sizes (concat (repeat r (inc q))
                      (repeat (- n r) q))]
    (second
     (reduce (fn [[i acc] sz]
               [(+ i sz) (conj acc (subvec v i (+ i sz)))])
             [0 []]
             sizes))))

(defn split-by-key [key window track]
  (let [segments (partition-by #(quot (get % key) window) track)]
    (->> (partition-all 2 1 segments)
         (map (fn [[s1 s2]]
                (if-let [overlap (first s2)]
                  (conj (vec s1) overlap)
                  s1)))
         (filter next))))

(defn add-ef-decline [splits]
  (if-let [base (:ef (first splits))]
    (map (fn [s]
           (if-let [cur (:ef s)]
             (assoc s :efr (/ cur base))
             s))
         splits)
    splits))

(defmulti split-track :by)

(defmethod split-track :time [{:keys [time]} track]
  (->> track
       (insert-boundaries time)
       (split-by-key :at time)))

(defmethod split-track :distance [{:keys [distance]} track]
  (split-by-key :distance distance track))

(defmethod split-track :even [{:keys [even]} track]
  (split-evenly even track))

(defn splits [opts track]
  (->> track
       (add-gait)
       (split-track opts)
       (map #(agg % opts))
       (add-ef-decline)))

(defn summary [track & {:as opts}]
  (-> track
      add-gait
      (agg opts)))

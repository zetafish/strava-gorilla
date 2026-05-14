(ns strava.track
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [strava.track.fit :as fit]
            [strava.track.gpx :as gpx]
            [strava.track.tcx :as tcx]))

(def cache-dir ".cache")

(defn- detect-format [path]
  (let [buf (byte-array 500)]
    (with-open [in (io/input-stream path)]
      (.read in buf))
    (if (= ".FIT" (String. buf 8 4))
      :fit
      (let [head (String. buf)]
        (cond
          (str/includes? head "TrainingCenterDatabase") :tcx
          (str/includes? head "<gpx") :gpx)))))

(defn remove-head [coll]
  (->> (drop-while #(zero? (:speed % 0)) coll)
       vec))

(defn remove-tail [coll]
  (->> (reverse coll)
       (drop-while #(zero? (:speed % 0)))
       reverse
       vec))

(defn- file->records [path]
  (case (detect-format path)
    :fit (fit/records path)
    :gpx (gpx/records path)
    :tcx (tcx/records path)))

(defn parse-file [fit-file]
  (fs/create-dirs cache-dir)
  (let [id (second (re-matches #".*_(\d+)_.*\.fit" fit-file))
        f (fs/file cache-dir (str id ".json"))]
    (if (fs/exists? f)
      (json/parse-string (slurp f) true)
      (let [coll (file->records fit-file)]
        (spit f (json/generate-string coll {:pretty true}))
        coll))))

(defn detect-record-duration [records]
  (let [timestamps (map :timestamp (take 20 records))
        deltas (map - (rest timestamps) timestamps)]
    (long (/ (reduce + deltas) (count deltas)))))

(defn add-duration [records]
  (let [dur (detect-record-duration records)]
    (mapv #(assoc % :duration dur) records)))

(defn bucket-fn [ts start-ts window]
  (* window (quot (- ts start-ts) window)))

(defn avg [k coll]
  (let [vals (keep k coll)]
    (when (seq vals)
      (double (/ (reduce + vals) (count vals))))))

(defn efficiency [{:keys [speed heart_rate]}]
  (when (and speed heart_rate)
    (/ speed heart_rate)))

(defn pace [{:keys [speed]}]
  (when (pos? speed)
    (int (/ 3600 (* 3.6 speed)))))

(defn kmph [{:keys [speed]}]
  (* 3.6 speed))

(defn enrich [m]
  (cond-> (assoc m :pace (pace m) :kmph (some-> (:speed m) (* 3.6)))
    (efficiency m) (assoc :ef_si (efficiency m)
                          :ef_metric (* 60 (efficiency m)))))

(defn agg [coll]
  (case (count coll)
    0 nil
    1 (assoc (first coll) :pts 1)
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
         (map agg))))

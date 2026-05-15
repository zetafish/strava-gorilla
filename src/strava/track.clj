(ns strava.track
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [strava.analysis :as analysis]
            [strava.parser.fit :as fit]
            [strava.parser.gpx :as gpx]
            [strava.parser.tcx :as tcx]))

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

;; Re-export analysis functions for backward compatibility
(def avg analysis/avg)
(def efficiency analysis/efficiency)
(def pace analysis/pace)
(def kmph analysis/kmph)
(def enrich analysis/enrich)
(def bucket-fn analysis/bucket-fn)
(def agg analysis/agg)
(def bucketize analysis/bucketize)

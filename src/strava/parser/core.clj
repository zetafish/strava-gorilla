(ns strava.parser.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [strava.parser.fit :as fit]
            [strava.parser.gpx :as gpx]
            [strava.parser.tcx :as tcx]
            [strava.util :as u]))

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

(defn parse-original [f]
  (let [path (str f)
        data (case (detect-format path)
               :fit (fit/records path)
               :gpx (gpx/records path)
               :tcx (tcx/records path))]
    (u/->kebab-case-keyword data)))

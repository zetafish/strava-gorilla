(ns strava.fit
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.data.csv :as csv]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint]
            [clojure.string :as str]
            [medley.core :as medley]
            [strava.gpxdata :as gpxdata]
            [strava.tcxdata :as tcxdata]))

(def cache-dir ".cache")

(def pp clojure.pprint/pprint)

(defn build-classpath []
  (p/shell "clj" "-P")
  (:out (p/shell {:out :string} "clj" "-Spath")))

(def classpath (delay (build-classpath)))

(defn- parse-header [header]
  (mapv (fn [col]
          (-> col
              (str/replace #"^record\." "")
              (str/replace #"\[.*\]" "")
              keyword))
        header))

(defn epoch->instant [epoch]
  (java.time.Instant/ofEpochSecond epoch))

(defn parse-fields [m]
  (let [parse-double #(some-> % parse-double)
        parse-long #(some-> % parse-long)]
    (-> m
        ;; double
        (update :speed parse-double)
        (update :enhanced_speed parse-double)
        (update :position_long parse-double)
        (update :position_lat parse-double)
        (update :step_length parse-double)
        (update :distance parse-double)
        (update :altitude parse-double)
        (update :enhanced_altitude parse-double)

        ;; long
        (update :cadence parse-long)
        (update :power parse-long)
        (update :heart_rate parse-long)
        (update :accumulated_power parse-long)

        ;; other
        (update :timestamp parse-long))))

(defn rolling-speed [records window]
  (mapv (fn [i]
          (let [curr (get records i)
                prev (get records (max 0 (- i window)))
                dt (- (:timestamp curr) (:timestamp prev))
                dd (- (:distance curr) (:distance prev))]
            (when (pos? dt)
              (* 1.0 (/ dd dt)))))
        (range (count records))))

(defn- fit->records [fit-path]
  (let [base (fs/create-temp-dir)
        csv-base (str base "/out")
        csv-data (str csv-base "_data.csv")
        csv-defn (str csv-base ".csv")]
    (p/shell "java" "-cp" (str/trim @classpath)
             "com.garmin.fit.csv.CSVTool"
             "--data" "record"
             "-e"
             "-deg"
             "-se"
             "-b" fit-path csv-base)
    (try
      (with-open [r (io/reader csv-data)]
        (let [[header & rows] (csv/read-csv r)
              keys (parse-header header)
              rows (->> rows
                        (mapv #(->> (zipmap keys %)
                                    (medley/remove-vals str/blank?)
                                    (medley/remove-keys (fn [k] (or (str/includes? (name k) " ")
                                                                    (str/blank? (name k)))))
                                    parse-fields))
                        (remove (comp nil? :speed))
                        (remove (comp nil? :distance)))
              start-ts (:timestamp (first rows))]
          (mapv (fn [row]
                  (assoc row :at (- (:timestamp row) start-ts)))
                rows)))
      (finally
        (io/delete-file csv-data true)
        (io/delete-file csv-defn true)
        (fs/delete base)))))

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

(defn- file->records [path]
  (case (detect-format path)
    :fit (fit->records path)
    :gpx (gpxdata/gpx->records path)
    :tcx (tcxdata/tcx->records path)))

(defn parse-file [fit-file]
  (fs/create-dirs cache-dir)
  (let [id (second (re-matches #".*_(\d+)_.*\.fit" fit-file))
        f (fs/file cache-dir (str id ".json"))]
    (if (fs/exists? f)
      (json/parse-string (slurp f) true)
      (let [coll (file->records fit-file)]
        (spit f (json/generate-string coll {:pretty true}))
        coll))))

(defn bucket-fn [ts start-ts window]
  (* window (quot (- ts start-ts) window)))

(defn avg [k coll]
  (let [vals (keep k coll)]
    (when (seq vals)
      (double (/ (reduce + vals) (count vals))))))

(defn agg [coll]
  (case (count coll)
    0 nil
    1 (assoc (first coll) :pts 1)
    {:at (:at (first coll))
     :timestamp (:timestamp (first coll))
     :heart_rate (some-> (avg :heart_rate coll) int)
     :cadence (some-> (avg :cadence coll) int)
     :step_length (some-> (avg :step_length coll) int)
     :distance (- (:distance (last coll)) (:distance (first coll)))
     :speed (/ (- (:distance (last coll)) (:distance (first coll)))
               (- (:timestamp (last coll)) (:timestamp (first coll))))
     :pts (count coll)}))

(defn bucketize [window records]
  (let [start-ts (:timestamp (first records))]
    (->> (group-by #(bucket-fn (:timestamp %) start-ts window) records)
         vals
         (sort-by (comp :timestamp first))
         (map agg))))

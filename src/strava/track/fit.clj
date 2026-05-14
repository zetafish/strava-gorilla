(ns strava.track.fit
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [medley.core :as medley]))

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

(defn records [fit-path]
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

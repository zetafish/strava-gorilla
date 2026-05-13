(ns strava.cache
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [strava.api :as api]))

(def cache-dir ".cache")

(def pattern #".cache/(\d+)\D+\.fit")

(defn load-index []
  (->> (fs/glob (fs/file cache-dir) "*")
       (map str)
       (keep #(re-matches pattern %))
       (map (fn [[f n]] [(parse-long n) f]))
       (into {})))

(def index (atom (load-index)))

(defn formatted-start-date [activity]
  (-> (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm")
      (.withZone (java.time.ZoneId/systemDefault))
      (.format (java.time.Instant/parse (:start_date activity)))))

(defn short-name [activity]
  (let [s (:name activity)]
    (subs s 0 (min (count s) 30))))

(defn distance [activity]
  (format "%5.1f km" (/ (:distance activity) 1000)))

(defn fit-file-name [activity]
  (str (:id activity)
       "_["
       (formatted-start-date activity)
       "]_["
       (distance activity)
       "]_["
       (short-name activity)
       "].fit"))

(defn sync-activity [activity]
  (if-let [f (get @index (:id activity))]
    f
    (let [f (fs/file cache-dir (fit-file-name activity))]
      (api/download-original (:id activity) f)
      (swap! index assoc (:id activity) f)
      f)))

(defn next-month [year month]
  (if (= 12 month)
    [(inc year) 1]
    [year (inc month)]))

(defn sync-month [year month]
  (let [after (format "%4d-%02d-01T00:00:00Z" year month)
        [year* month*] (next-month year month)
        before (format "%4d-%02d-01T00:00:00Z" year* month*)
        coll (api/list-activities :per-page 200 :after after :before before)]

    (doseq [x coll]
      (sync-activity x))))

(sync-month 2026 1)

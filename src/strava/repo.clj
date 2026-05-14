(ns strava.repo
  (:require [babashka.fs :as fs]
            [clojure.pprint]
            [clojure.string :as str]
            [strava.api :as api]))

(def repo-dir ".repo")

(def desc-dir ".desc")

(def pattern #".repo/(\d+).*\.fit")

(defn load-index []
  (->> (fs/glob (fs/file repo-dir) "*")
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
  (let [s (:name activity "noname")]
    (str/replace (str/trim (subs s 0 (min (count s) 30)))
                 "/"
                 "_")))

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

(defn get-fit-file-by-activity [activity]
  (if-let [f (get @index (:id activity))]
    f
    (let [f (fs/file repo-dir (fit-file-name activity))]
      (api/download-original (:id activity) f)
      (swap! index assoc (:id activity) f)
      f)))

(defn get-description-by-activity-id [id]
  (let [desc (api/fetch-description id)
        f (fs/file desc-dir (str id ".txt"))]
    (fs/create-dirs desc-dir)
    (spit f desc)))

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
      (println (fit-file-name x))
      (get-fit-file-by-activity x))))

(defn xform-by-pattern [pat]
  (filter #(str/includes? (str/lower-case %) (str/lower-case pat))))

(defn find-by-pattern [& coll]
  (let [xf (apply comp (map #(xform-by-pattern %) coll))]
    (transduce xf conj (vals @index))))

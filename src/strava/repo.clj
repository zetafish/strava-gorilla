(ns strava.repo
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.pprint]
            [clojure.string :as str]
            [strava.api :as api]))

(def repo-dir ".repo")

(def desc-dir ".desc")

(def pattern #".repo/.*_(\d+)_.*\.fit")

(defn load-index []
  (->> (fs/list-dir repo-dir)
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
    (str/replace (str/trim (subs s 0 (min (count s) 50)))
                 "/"
                 "_")))

(defn distance [activity]
  (format "%5.1f km" (/ (:distance activity) 1000)))

(defn fit-file-name [activity]
  (str "["
       (formatted-start-date activity)
       "]_["
       (distance activity)
       "]_"
       (:id activity)
       "_["
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
    (spit f desc)
    desc))

(def activities-dir ".activities")

(defn extract-id [fit-path]
  (some-> (re-matches pattern (str fit-path)) second parse-long))

(defn find-activity [id]
  (some (fn [f]
          (->> (json/parse-string (slurp (str f)) true)
               (filter #(= (:id %) id))
               first))
        (fs/list-dir activities-dir)))

(defn xform-by-pattern [pat]
  (filter #(str/includes? (str/lower-case %) (str/lower-case pat))))

(defn find-by-pattern [& coll]
  (let [xf (apply comp (map #(xform-by-pattern %) coll))]
    (transduce xf conj (vals @index))))

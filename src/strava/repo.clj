(ns strava.repo
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.pprint]
            [clojure.string :as str]
            [strava.api :as api]
            [strava.tags :as tags]))

(def repo-dir ".repo")

(def desc-dir ".desc")

(def activities-dir ".activities")

(defn load-activities []
  (->> (fs/list-dir activities-dir)
       (map str)
       (map slurp)
       (mapcat #(json/decode % true))))

(def activities (atom (load-activities)))

(defn fit-file-name [activity]
  (str (:id activity) ".fit"))

(defn get-fit-file-by-activity [activity]
  (let [f (fs/file repo-dir (fit-file-name activity))]
    (when-not (fs/exists? f)
      (api/download-original (:id activity) f))
    f))

(defn get-description-by-activity-id [id]
  (let [desc (api/fetch-description id)
        f (fs/file desc-dir (str id ".txt"))]
    (fs/create-dirs desc-dir)
    (spit f desc)
    desc))

(defn extract-id [fit-path]
  (some-> (re-matches #".*/(\d+)\.fit" (str fit-path)) second parse-long))

(defn find-activities [{:keys [limit
                               id from to pattern hr-min hr-max
                               dist-min dist-max
                               tag no-tag]}]
  (let [start-date #(some-> (:start_date %) (subs 0 10))]
    (cond->> @activities
      id (filter #(= id (:id %)))
      from (filter #(>= 0 (compare from (start-date %))))
      to (filter #(<= 0 (compare to (start-date %))))
      pattern (filter #(str/includes? (str/lower-case (:name %)) (str/lower-case pattern)))
      hr-min (filter #(or (not (:has_heartrate %)) (>= (:average_heartrate %) hr-min)))
      hr-max (filter #(or (not (:has_heartrate %)) (<= (:average_heartrate %) hr-max)))
      dist-min (filter #(>= (:distance %) (* 1000 dist-min)))
      dist-max (filter #(<= (:distance %) (* 1000 dist-max)))
      tag (filter #(every? (fn [t] (contains? (tags/all-tags (:id %) %) (keyword t))) tag))
      no-tag (filter #(every? (fn [t] (not (contains? (tags/all-tags (:id %) %) (keyword t)))) no-tag))
      true (sort-by :start_data)
      limit (take limit))))

(defn find-activity [id]
  (first (find-activities {:id id})))

(defn find-by-pattern [pat]
  (find-activities {:pattern pat}))

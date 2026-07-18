(ns strava.repo
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.pprint]
            [clojure.string :as str]
            [strava.api :as api]
            [strava.parser.core :as parser]
            [strava.tags :as tags]))

(def originals-dir ".data/originals")
(def tracks-dir ".data/tracks")
(def activities-dir ".data/activities")
(def descriptions-dir ".data/descriptions")

(defn load-activities []
  (->> (fs/list-dir activities-dir)
       (map str)
       (map slurp)
       (mapcat #(json/decode % true))))

(def activities (atom (load-activities)))

(defn- original-file [id]
  (fs/file originals-dir (str id ".fit")))

(defn- track-file [id]
  (fs/file tracks-dir (str id ".json")))

(defn- description-file [id]
  (fs/file descriptions-dir (str id ".txt")))

(defn- activities-file [year month]
  (fs/file activities-dir (format "%4d-%02d.json" year month)))

(defn ensure-original-file [id]
  (let [f (original-file id)]
    (when-not (fs/exists? f)
      (api/download-original id f)) 3
    f))

(defn ensure-track-file [id]
  (let [f (track-file id)]
    (when-not (fs/exists? f)
      (spit (str f)
            (json/encode (parser/parse-original (ensure-original-file id))
                         {:pretty true})))
    f))

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
      true (filter #(= "Run" (:sport_type %)))
      true (sort-by :start_data)
      limit (take limit))))

(defn round [v]
  (int (Math/round v)))

(defn build-pred [where]
  (letfn [(get-prop [m k]
            (case k
              :date (some-> m :start_date (subs 0 10))
              :heart_rate (some-> m :average_heartrate round)
              :distance (some-> m :distance (/ 1000) round)
              (get m k)))
          (build-op [op]
            (case op
              :>= #(>= (compare %1 %2) 0)
              :<= #(<= (compare %1 %2) 0)
              :> #(> (compare %1 %2) 0)
              :< #(< (compare %1 %2) 0)
              := #(= %1 %2)))]
    (case (first where)
      :and (apply every-pred (map build-pred (rest where)))
      :or (apply some-fn (map build-pred (rest where)))
      (let [[op lhs rhs] where
            op (build-op op)]
        (cond
          (keyword lhs) #(op (get-prop % lhs) rhs)
          (keyword rhs) #(op lhs (get-prop % rhs))
          :else (throw (ex-info "invalid clause" {:clause where})))))))

;; (build-pred [:and [:= :kmph 10]])
;; (build-pred [:and [:= :date "2026-05-01"]])

;; ((build-pred [:>= :d "1"]) {:d "2"})

;; (compare "1" "2")

;; (defn find-x [where]
;;   (filter (build-pred where) @activities))

;; (defn find-activities [selector]
;;   (filter (build-pred selector) @activities))

(defn find-by-pattern [pattern]
  (->> (find-activities {:pattern pattern})
       (map (comp str ensure-original-file :id))))

(defn get-activity [id]
  (first (find-activities {:id id})))

(defn get-track [id]
  (ensure-track-file id)
  (json/decode (slurp (track-file id)) true))

(defn get-description [id]
  (let [f (description-file id)]
    (when-not (fs/exists? f)
      (spit f (api/fetch-description id)))
    (slurp f)))

(defn sync-month [year month]
  (let [f (activities-file year month)
        after (format "%4d-%02d-01T00:00:00Z" year month)
        month* (inc (rem month 12))
        year* (+ year (if (= 12 month) 1 0))
        before (format "%4d-%02d-01T00:00:00Z" year* month*)
        coll (api/list-activities :per-page 200 :after after :before before)]
    (spit f (json/generate-string coll {:pretty true}))
    (reset! activities (load-activities))
    nil))

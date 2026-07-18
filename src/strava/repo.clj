(ns strava.repo
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.pprint]
            [clojure.string :as str]
            [strava.parser.core :as parser]
            [strava.scrape.calendar :as scrape-cal]
            [strava.scrape.fit :as scrape-fit]
            [strava.scrape.summary :as scrape-sum]
            [strava.tags :as tags]))

(def originals-dir ".data/originals")
(def tracks-dir ".data/tracks")
(def activities-dir ".data/activities")

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

(defn- activities-file [year month]
  (fs/file activities-dir (format "%4d-%02d.json" year month)))

(defn ensure-original-file [id]
  (let [f (original-file id)]
    (when-not (fs/exists? f)
      (scrape-fit/download id f))
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

(defn find-by-pattern [pattern]
  (find-activities {:pattern pattern}))

(defn fit-file [activity]
  (str (ensure-original-file (:id activity))))

(defn get-activity [id]
  (first (find-activities {:id id})))

(defn get-track [id]
  (ensure-track-file id)
  (json/decode (slurp (track-file id)) true))

(defn get-description [id]
  (:description (get-activity id)))

(defn- write-month-summaries [year month rows]
  (let [summaries (doall
                   (for [row rows]
                     (do (println "  " (:date row) (:id row) (:name row))
                         (let [fit (str (ensure-original-file (:id row)))]
                           (scrape-sum/merge-summary row fit)))))]
    (spit (activities-file year month)
          (json/generate-string summaries {:pretty true}))
    summaries))

(defn sync-year
  "Scrape the calendar for `year`, download any missing FIT files,
   derive API-summary maps, write one .data/activities/YYYY-MM.json per month."
  [year]
  (fs/create-dirs activities-dir)
  (let [rows (scrape-cal/parse-year year (scrape-cal/fetch-year-html year))
        by-month (group-by #(Long/parseLong (subs (:date %) 5 7)) rows)]
    (doseq [[month month-rows] (sort-by key by-month)]
      (println :sync (format "%04d-%02d" year month) (count month-rows))
      (write-month-summaries year month month-rows))
    (reset! activities (load-activities))
    nil))

(defn sync-month
  "Sync a single month. Fetches the year calendar (one HTTP call) and keeps
   only rows in the requested month."
  [year month]
  (fs/create-dirs activities-dir)
  (let [prefix (format "%04d-%02d" year month)
        rows (->> (scrape-cal/parse-year year (scrape-cal/fetch-year-html year))
                  (filter #(str/starts-with? (:date %) prefix)))]
    (println :sync prefix (count rows))
    (write-month-summaries year month rows)
    (reset! activities (load-activities))
    nil))

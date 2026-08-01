(ns strava.repo
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.pprint]
            [clojure.string :as str]
            [strava.cache :as cache]
            [strava.log :as log]
            [strava.parser.core :as parser]
            [strava.scrape.activity :as activity]
            [strava.scrape.calendar :as calendar]
            [strava.scrape.original :as original]
            [strava.tags :as tags]))

(def calendars-dir ".data/calendars")
(def originals-dir ".data/originals")
(def tracks-dir ".data/tracks")
(def activities-dir ".data/activities")

(defn get-calendar [year & {:keys [force]}]
  (when force
    (cache/evict! :calendars year))
  (cache/through-cache :calendars year #(calendar/fetch year)))

(defn get-activity [id & {:keys [force]}]
  (when force
    (cache/evict! :activities id))
  (cache/through-cache :activities id #(activity/fetch id)))

(defn get-original [id & {:keys [force]}]
  (let [f (fs/file originals-dir (str id ".fit"))]
    (when (or force (not (fs/exists? f)))
      (original/download id f))
    f))

(defn get-track [id & {:keys [force] :as opts}]
  (when force
    (cache/evict! :tracks id))
  (cache/through-cache :tracks id
                       #(parser/parse-original (get-original id opts))))

(defn load-calendars []
  (->> (fs/list-dir calendars-dir)
       (map (comp slurp str))
       (mapcat #(json/decode % true))))

(defn load-activities []
  (->> (fs/list-dir activities-dir)
       (map (comp slurp str))
       (map (comp #(json/decode % true)))))

(defn filter-activities [{:keys [limit
                                 id from to pattern hr-min hr-max
                                 dist-min dist-max
                                 tag no-tag]}
                         activities]
  (let [start-date #(some-> (:start_date %) (subs 0 10))]
    (cond->> activities
      true (filter #(= "Run" (:activity_type %)))
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

(defn- sync-activities [coll]
  (->> coll
       (map #(future
               (log/info %)
               (get-activity (:id %))
               (get-track (:id %))))
       (map deref)
       doall))

(defn sync-year [year]
  (->> (get-calendar year {:force true})
       (sync-activities)))

(defn sync-month [year month]
  (->> (get-calendar year {:force true})
       (filter #(= (format "%4d-%02d" year month) (subs (:date %) 0 7)))
       (sync-activities)))

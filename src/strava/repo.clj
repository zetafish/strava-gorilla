(ns strava.repo
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.pprint]
            [strava.cache :as cache]
            [strava.parser.core :as parser]
            [strava.scrape.activity :as activity]
            [strava.scrape.calendar :as calendar]
            [strava.scrape.original :as original]))

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

(def ^:private ^:const sync-jitter-min-ms 500)
(def ^:private ^:const sync-jitter-max-ms 2000)

(defn- jitter-sleep! []
  (Thread/sleep (+ sync-jitter-min-ms
                   (rand-int (- sync-jitter-max-ms sync-jitter-min-ms)))))

(defn- sync-activities [coll]
  (doseq [a coll
          :let [id (:id a)
                activity-miss? (not (cache/has? :activities id))
                track-miss? (not (cache/has? :tracks id))]]
    (println a)
    (when activity-miss?
      (get-activity id)
      (jitter-sleep!))
    (when track-miss?
      (get-track id)
      (jitter-sleep!))))

(defn sync-year [year]
  (->> (get-calendar year {:force true})
       (sync-activities)))

(defn sync-month [year month]
  (->> (get-calendar year {:force true})
       (filter #(= (format "%4d-%02d" year month) (subs (:date %) 0 7)))
       (sync-activities)))

(ns strava.db
  (:require [pod.babashka.postgresql :as pg]
            [strava.repo :as repo]))

(def db {:dbtype "postgresql" :host "127.0.0.1" :port 5434 :dbname "endy.kasanardjo"})

(pg/execute! db ["select count(*) from activities"])

(pg/execute! db ["select count(*) from track_points where activity_id=?"
                 18713331742])

(time (count (pg/execute! db ["select * from track_points where activity_id=?"
                              18713331742])))

(time (count (pg/execute! db ["select activity_id from track_points where activity_id=?"
                              18713331742])))

(def activities (repo/find-activities {:from "2026-05-01"}))
(def ids (map :id activities))
(def s (str "(" (clojure.string/join "," ids) ")"))

(time (def points (pg/execute! db
                               [(str "select activity_id from track_points where activity_id in " s)])))

(count points)

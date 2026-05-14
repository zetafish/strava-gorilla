(ns strava.user
  (:require [cheshire.core :as json]
            [strava.api :as api]
            [strava.repo :as repo]))

(def x (api/get-activity 17169259426))
(api/refresh-token!)
(println x)
(spit "x.json" (json/encode x {:pretty true}))

(api/exchange-code! "53988583e82acc366830c7a3af8352333a546562")

(def bulk (api/list-activities :page 10))
(spit "bulk.json" (json/encode bulk {:pretty true}))

(repo/get-description-by-activity-id 1689730133)

(repo/sync-month 2018 7)

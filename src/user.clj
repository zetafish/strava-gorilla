(ns user
  (:require [charred.api :as charred]
            [cheshire.core :as json]))

(repo/sync-month 2026 6)
(def id (:id (first (repo/find-activities {:from "2026-06-01"}))))
(def track (repo/get-track id))

(def splits (track/splits {:strategy :distance
                           :distance 1000}
                          track))

(def json (slurp (str ".data/tracks/" id ".json")))

(def f ".data/tracks/18738180900.json")
(def f-100k ".data/tracks/13966884881.json")
(def data (slurp f-100k))

(time (count (charred/read-json data)))
(time (count (json/decode data)))

(time (def dirty  (slurp "dirty.json")))

(time (count (charred/read-json dirty)))

(defn spit-csv-json [coll f]
  (let [header (keys coll)]
    (spit f (json/encode header) :append true)))

(spit-csv-json (json/decode data true) "x.json")

(spit "dirty.json" (json/encode (json/decode data)))

(api/refresh-token!)

(ns strava.user
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cognitect.transit :as transit]
            [strava.api :as api]
            [strava.fit :as fit]
            [strava.gpxdata :as gpxdata]
            [strava.repo :as repo]
            [strava.tcxdata :as tcxdata]))

(def x (api/get-activity 17169259426))
(api/refresh-token!)
(println x)
(spit "x.json" (json/encode x {:pretty true}))

(api/exchange-code! "53988583e82acc366830c7a3af8352333a546562")
(api/exchange-code! "f70140e66701f0450ad847a01e314c56b49cd19c")
(api/exchange-code! "a4af5eaf978cd15860404af61c36b1590a80c3ac")
(api/exchange-code! "b54bf71cd542c445d708548050b68f8efc358209")

(def bulk (api/list-activities :page 10))
(spit "bulk.json" (json/encode bulk {:pretty true}))

(repo/get-description-by-activity-id 1689730133)

(def blob (fit/parse-file ".repo/[2023-09-30 05:59]_[245.5 km]_9956922752_[Spartathlon official time 35:0].fit"))

(spit "big.json" (json/encode blob {:pretty true}))

(spit "big.transit" ())

(defn spit-transit [f m]
  (with-open [out (io/output-stream f)]
    (transit/write (transit/writer out :msgpack) m)))

(defn read-transit [f]
  (with-open [in (io/input-stream f)]
    (transit/read (transit/reader in :msgpack))))

(time (count (json/decode (slurp "big.json"))))

(spit-transit "big.transit" blob)
(spit "big.edn" blob)
(spit "big.json"  (json/encode blob))

(time
 (count (json/decode (slurp "big.json") true))) ; 4824 ms

(time
 (count
  (edn/read-string (slurp "big.edn"))))

(time
 (count
  (read-transit "big.transit")))

;; 2609ms
(gpxdata/gpx->records "amazfit.xml")
(tcxdata/tcx->records "suunto.xml")

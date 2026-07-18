(ns strava.core
  (:require [cheshire.core :as json]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [strava.repo :as repo]
            [strava.table :as table]
            [strava.track :as track]))

(defn summarize-activity [activity]
  (let [track (repo/get-track (:id activity))
        agg (track/agg track)]
    (merge agg
           (select-keys activity [:id :name]))))

(def sittard-id 18456497751)

(def track (repo/get-track 13966884881))

(def track (json/decode (slurp ".data/tracks/18456497751.json") true))

;; (def sittard-track (repo/get-track sittard-id))


(time (def track (repo/get-track sittard-id)))

(def item (first (drop 1000 track)))

(def data [(vec (keys item))
           (vec (vals item))])

(defn items->header-rows [items]
  (when (seq items)
    (let [headers (vec (keys (first items)))
          rows (map (fn [item] (mapv #(get item %) headers)) items)]
      (cons headers rows))))

(defn header+rows->items [[header & rows]]
  (let [header (map keyword header)]
    (mapv #(zipmap header %) rows)))

(defn read-json [filename]
  (json/decode (slurp filename) true))

(table/print-table [:at :distance :kmph :heart_rate :step_length :cadence :ef]
                   (track/splits {:strategy :distance
                                  :distance 1
                                  ;; :seconds 3600
                                  }
                                 (repo/get-track 13966884881)
                                 ;; track
                                 ))

(defn build-overview [activities]
  (pmap (fn [activity]
          (merge (track/agg (repo/get-track (:id activity)))
                 (select-keys activity [:id :name])))
        activities))

(table/print-table [:id :kmph :heart_rate :ef :name]
                   (build-overview (repo/find-activities {:from "2026-06-01"})))

;; (time (count (repo/get-track 18713331742)))

;; (def activities (repo/find-activities {:from "2026-05-01"}))
;; (def ids (map :id activities))
;; (time (def tracks (doall (mapcat repo/get-track  ids))))
;; (count tracks)

;; (def run-100-km (repo/get-track 13966884881))

;; (track/splits {:strategy :distance
;;                :distance 10000}
;;               run-100-km)

;; (->> run-100-km
;;      (map :distance)
;;      (partition-by identity)
;;      (map (juxt first count))
;;      (remove #(= 1 (second %)))
;;      ;; (map count)
;;      ;; (partition-by identity)
;;      ;; (map (juxt first count))
;;      )

;; (->> run-100-km
;;      (drop 10000)
;;      ;; (filter #(= nil (:distance %)))
;;      )

;; (repo/sync-month 2026 6)

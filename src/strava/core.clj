(ns strava.core
  (:require [clojure.string :as str]
            [strava.api :as api]
            [strava.fit :as fit])
  (:import (java.time
            Duration
            Instant
            Period)))

(defn dump-csv [fit-file csv-file]
  (let [header [:timestamp :at :ef_si :ef_metric :distance :speed :heart_rate :cadence :step_length]
        s (->> (fit/fit->records fit-file)
               (map #(let [ef-si (fit/efficiency %)]
                       (assoc %
                              :ef_si ef-si
                              :ef_metric (some-> ef-si (* 60)))))
               (map (apply juxt header))
               ;; (filter #(every? some? %))
               (map #(str/join "," %))
               (str/join \newline))]
    (spit csv-file
          (str/join \newline
                    [(str/join "," (map name header))
                     s]))))

(defn enrich [m]
  (if-let [ef-si (fit/efficiency m)]
    (assoc m
           :ef_si ef-si
           :ef_metric (* 60 ef-si))
    m))

(defn build-csv [coll]
  (let [header [:timestamp :at :ef_si :ef_metric :distance :speed :heart_rate :cadence :step_length]
        data (->> coll
                  (map enrich)
                  (map (apply juxt header))
                  (map #(str/join "," %)))]
    (str/join \newline
              (concat [(str/join "," (map name header))]
                      (map #(str/join "," data))))))

(defn process-activity [id name]
  (let [fit-file (str id "-" name ".fit")
        csv-file (str id "-" name ".csv")]
    (api/download-original id fit-file)
    (dump-csv fit-file csv-file)))

(process-activity 18488439809 "morning-run")

;; (process-activity 5793097926 "assen")

(def data (fit/fit->records "18488439809-morning-run.fit"))
(def d2 (fit/bucketize data 60))

(do
  (println (format "%8s  %3s  %3s   %3s"
                   "time" "HR" "pace" "km/h"))
  (doseq [d d2]
    ;; at hr pace

    (println (format "%8s  %3d  %3s  %3.2f"
                     (:at d)
                     (:heart_rate d)
                     (fit/duration (fit/pace d))
                     (fit/kmph d)))))

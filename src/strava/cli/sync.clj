(ns strava.cli.sync
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [strava.api :as api]
            [strava.repo :as repo]))

(def spec {:year {:alias :y :require true :coerce :int}
           :month {:alias :m :coerce :int}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println (cli/format-opts {:spec spec}))
    true))

(def activities-dir ".activities")

(defn save-activities [year month coll]
  (fs/create-dirs activities-dir)
  (let [f (str activities-dir "/" (format "%4d-%02d.json" year month))]
    (spit f (json/generate-string coll {:pretty true}))))

(defn sync-month [year month]
  (println :sync-month year month)
  (let [after (format "%4d-%02d-01T00:00:00Z" year month)
        month* (inc (rem month 12))
        year* (+ year (if (= 12 month) 1 0))
        before (format "%4d-%02d-01T00:00:00Z" year* month*)
        coll (api/list-activities :per-page 200 :after after :before before)]
    (save-activities year month coll)
    (doseq [x coll]
      (println (:start_date x) (:id x) (:name x))
      (repo/get-fit-file-by-activity x))))

(defn sync-year [year]
  (doseq [month (range 1 13)]
    (sync-month year month)))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})]
        (cond
          (not (:month opts)) (sync-year (:year opts))
          :else (sync-month (:year opts) (:month opts))))))

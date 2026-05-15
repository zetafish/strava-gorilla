(ns strava.cli.eff
  (:require [babashka.cli :as cli]
            [cheshire.core :as json]
            [clojure.string :as str]
            [strava.repo :as repo]
            [strava.track :as track]
            [strava.analysis :as analysis]))

(defn at->str [seconds]
  (when seconds
    (let [h (quot seconds 3600)
          m (rem (quot seconds 60) 60)
          s (rem seconds 60)]
      ;; (str h "h" m "m" s "s")
      (format "%02d:%02d:%02d" h m s))))

(defn pace->str [seconds]
  (when seconds
    (let [m (quot seconds 60)
          s (rem seconds 60)]
      (str m "m" s "s"))))

(defn ts->str [ts]
  (java.time.Instant/ofEpochSecond ts))

(defn format-point [m]
  (-> m
      (update :timestamp ts->str)
      (update :at at->str)
      (update :ef_si #(some-> % (as-> v (format "%5.3f" v))))
      (update :ef_metric #(some-> % (as-> v (format "%5.3f" v))))
      (update :speed #(some-> % (as-> v (format "%3.1f" v))))
      (update :kmph #(some-> % (as-> v (format "%3.1f" v))))
      (update :distance #(some-> % long))
      (update :pace pace->str)))

(defn print-csv [coll]
  (let [header [:timestamp :at :ef_si :ef_metric :distance :speed :heart_rate :cadence :step_length :pts]
        rows (->> coll
                  (map (apply juxt header))
                  (map #(str/join "," %)))]
    (println (str/join \newline
                       [(str/join "," (map name header))
                        (str/join \newline rows)]))))

(defn print-json [coll]
  (println (json/encode coll {:pretty true})))

(defn print-table [coll]
  (println (str/join (repeat 50 "-")))
  (let [header ["Offset" "HR" "Pace" "km/h" "dist" "EF" "cad" "slen" "pts"]
        fmt "%10s %4s %8s %5s %10s %7s %5s %5s %5s"]

    (println (apply format fmt header))
    (println (str/join (repeat 50 "-")))
    (doseq [x coll]
      (println (format fmt
                       (:at x)
                       (:heart_rate x)
                       (:pace x)
                       (:kmph x)
                       (:distance x)
                       (:ef_metric x)
                       (:cadence x)
                       (:step_length x)
                       (:pts x))))))

(defn print-summary [summary quarters activity]
  (println (str/join (repeat 50 "-")))
  (when activity
    (println (format "  ID:         %s" (:id activity)))
    (println (format "  Name:       %s" (:name activity)))
    (println (format "  Start:      %s" (:start_date_local activity))))
  (println (format "  Distance:   %.1f km" (/ (:distance summary) 1000.0)))
  (println (format "  Duration:   %s" (at->str (:duration summary))))
  (println (format "  Avg HR:     %s" (:heart_rate summary)))
  (println (format "  Avg Pace:   %s" (:pace summary)))
  (println (format "  Avg EF:     %s" (:ef_metric summary)))
  (let [efs (keep :ef_metric quarters)]
    (when (= 4 (count efs))
      (println (format "  EF Q1-Q4:   %.3f  %.3f  %.3f  %.3f"
                       (nth efs 0) (nth efs 1) (nth efs 2) (nth efs 3)))
      (let [decline (* 100.0 (/ (- (first efs) (last efs)) (first efs)))]
        (println (format "  EF Decline: %.1f%%" decline))))))

(def spec {:pattern {:alias :p :coerce [] :require true}
           :interval {:alias :i :coerce analysis/parse-at :default 3600 :desc "Bucket interval (e.g. 5m, 10s, 1h)"}
           :format {:alias :f :default "table" :validate #{"table" "csv" "json"}}
           :from {:coerce analysis/parse-at :desc "Start time as offset (e.g. 21h15, 3h, 120m, 7200s)"}
           :to {:coerce analysis/parse-at :desc "End time as offset (e.g. 21h15, 3h, 120m, 7200s)"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println (cli/format-opts {:spec spec}))
    true))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})]
        (if-let [f (first (apply repo/find-by-pattern (:pattern opts)))]
          (let [activity (some-> (repo/extract-id f) repo/find-activity)
                records (cond->> (-> (track/parse-file f) track/add-duration track/remove-head track/remove-tail)
                          (:from opts) (drop-while #(< (:at %) (:from opts)))
                          (:to opts) (take-while #(< (:at %) (:to opts))))
                coll (->> (track/bucketize (:interval opts) records)
                          (map format-point))
                summary (-> (track/agg records) format-point)
                q (quot (count records) 4)
                quarters (mapv #(track/agg %)
                               [(take q records)
                                (->> records (drop q) (take q))
                                (->> records (drop (* 2 q)) (take q))
                                (drop (* 3 q) records)])]
            (case (:format opts)
              "table" (do (print-table coll) (print-summary summary quarters activity))
              "csv" (print-csv coll)
              "json" (print-json coll)))
          (println "No fit file found for" (:pattern opts))))))

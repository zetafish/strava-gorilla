(ns strava.core
  (:require [babashka.cli :as cli]
            [clojure.string :as str]
            [strava.api :as api]
            [strava.fit :as fit]
            [strava.repo :as repo])
  (:import (java.time
            Duration
            Instant
            Period)))

(defn at->str [seconds]
  (when seconds
    (let [h (quot seconds 3600)
          m (rem (quot seconds 60) 60)
          s (rem seconds 60)]
      (str h "h" m "m"))))

(defn pace->str [seconds]
  (when seconds
    (let [m (quot seconds 60)
          s (rem seconds 60)]
      (str m "m" s "s"))))

(defn efficiency [{:keys [speed heart_rate]}]
  (when (and speed heart_rate)
    (/ speed heart_rate)))

(defn pace [{:keys [speed]}]
  (when (pos? speed)
    (int (/ 3600 (* 3.6 speed)))))

(defn kmph [{:keys [speed]}]
  (* 3.6 speed))

(defn enrich [m]
  (if-let [ef-si (efficiency m)]
    (assoc m
           :ef_si ef-si
           :ef_metric (* 60 ef-si)
           :pace (pace m)
           :kmph (kmph m))
    m))

(defn build-csv [coll]
  (let [header [:timestamp :at :ef_si :ef_metric :distance :speed :heart_rate :cadence :step_length :pts]
        rows (->> coll
                  (map enrich)
                  (map (apply juxt header))
                  (map #(str/join "," %)))]
    (str/join \newline
              [(str/join "," (map name header))
               (str/join \newline rows)])))

(defn print-table [coll]
  (println (str/join (repeat 50 "-")))
  (let [header ["Time" "HR" "Pace" "km/h" "EF" "cad" "slen" "pts"]
        fmt-h "%10s %4s %8s %5s %7s %5s %5s %5s"
        fmt-r "%10s %4s %8s %5.1f %7.5f %5s %5s %5s"]

    (println (apply format fmt-h header))
    (println (str/join (repeat 50 "-")))
    (doseq [x coll]
      (println (format fmt-r
                       (at->str (:at x))
                       (:heart_rate x)
                       (pace->str (:pace x))
                       (:kmph x)
                       (:ef_metric x)
                       (:cadence x)
                       (:step_length x)
                       (:pts x))))))

(defn parse-at
  "Parse duration/offset string to seconds. Supports: 1h, 30m, 90s, 1h30, 21h15m, 21h15"
  [s]
  (when s
    (let [s (str/trim s)]
      (cond
        ;; 21h15 or 1h30 (hours + minutes, m optional)
        (re-matches #"\d+h\d+m?" s)
        (let [[_ h m] (re-matches #"(\d+)h(\d+)m?" s)]
          (+ (* (parse-long h) 3600) (* (parse-long m) 60)))

        ;; 5h
        (re-matches #"\d+h" s)
        (* (parse-long (str/replace s "h" "")) 3600)

        ;; 30m
        (re-matches #"\d+m" s)
        (* (parse-long (str/replace s "m" "")) 60)

        ;; 90s
        (re-matches #"\d+s" s)
        (parse-long (str/replace s "s" ""))

        :else
        (do (println (str "Invalid time format: " s))
            (System/exit 1))))))

(def spec {:pattern {:alias :p :coerce [] :require true}
           :interval {:alias :i :coerce :int :default 3600}
           :from {:coerce parse-at :desc "Start time as offset (e.g. 21h15, 3h, 120m, 7200s)"}
           :to {:coerce parse-at :desc "End time as offset (e.g. 21h15, 3h, 120m, 7200s)"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println (cli/format-opts {:spec spec}))
    true))

(defn run [opts]
  (if-let [f (first (apply repo/find-by-pattern (:pattern opts)))]
    (->> (fit/parse-file f)
         (fit/bucketize (:interval opts))
         (map enrich)
         (print-table))
    (println "No fit file found for" (:pattern opts))))

(defn -main [& args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})]
        (run opts))))

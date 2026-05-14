(ns strava.cli.eff
  (:require [babashka.cli :as cli]
            [cheshire.core :as json]
            [clojure.string :as str]
            [strava.fit :as fit]
            [strava.repo :as repo]))

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

(defn format-point [m]
  (-> m
      (update :timestamp ts->str)
      (update :at at->str)
      (update :ef_si #(format "%5.3f" %))
      (update :ef_metric #(format "%5.3f" %))
      (update :speed #(format "%3.1f" %))
      (update :kmph #(format "%3.1f" %))
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
  (let [header ["Offset" "HR" "Pace" "km/h" "EF" "cad" "slen" "pts"]
        fmt "%10s %4s %8s %5s %7s %5s %5s %5s"]

    (println (apply format fmt header))
    (println (str/join (repeat 50 "-")))
    (doseq [x coll]
      (println (format fmt
                       (:at x)
                       (:heart_rate x)
                       (:pace x)
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
           :format {:alias :f :default "table" :validate #{"table" "csv" "json"}}
           :from {:coerce parse-at :desc "Start time as offset (e.g. 21h15, 3h, 120m, 7200s)"}
           :to {:coerce parse-at :desc "End time as offset (e.g. 21h15, 3h, 120m, 7200s)"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println (cli/format-opts {:spec spec}))
    true))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})]
        (if-let [f (first (apply repo/find-by-pattern (:pattern opts)))]
          (let [coll (->> (cond->> (fit/parse-file f)
                            (:from opts) (drop-while #(< (:at %) (:from opts)))
                            (:to opts) (take-while #(< (:at %) (:to opts))))
                          (fit/bucketize (:interval opts))
                          (map enrich)
                          (map format-point))]
            (case (:format opts)
              "table" (print-table coll)
              "csv" (print-csv coll)
              "json" (print-json coll)))
          (println "No fit file found for" (:pattern opts))))))

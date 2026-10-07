(ns strava.util
  (:require [babashka.cli :as cli]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [medley.core :as medley]
            [strava.me :as me])
  (:import (java.time
            DayOfWeek
            LocalDate)
           (java.time.temporal
            TemporalAdjusters)))

(defn avg [coll k]
  (let [vals (keep k coll)]
    (when (seq vals)
      (double (/ (reduce + vals) (count vals))))))

(defn sum [coll k]
  (reduce + (keep k coll)))

(defn speed->pace [speed]
  (when (and speed (pos? speed)) (int (/ 3600 (* 3.6 speed)))))

(defn speed->kmph [speed]
  (when speed (* 3.6 speed)))

(defn pace->kmph
  "Parse a pace like \"5:30\" (min:sec per km) into km/h."
  [s]
  (let [[_ m sec] (re-matches #"(\d+):(\d{2})" (str/trim s))]
    (when-not m
      (throw (ex-info (str "Invalid pace, expected m:ss: " s) {:pace s})))
    (/ 3600.0 (+ (* 60 (parse-long m)) (parse-long sec)))))

(defn ef [speed heart-rate]
  (when (and speed heart-rate)
    (* 60 (/ speed (- heart-rate  me/resting-hr)))))

(defn with-derived-metrics [{:keys [speed heart-rate] :as m}]
  (assoc m
         :pace (speed->pace speed)
         :kmph (speed->kmph speed)
         :ef (ef speed heart-rate)))

(defn parse-time [s]
  (when s
    (let [s (str/trim s)]
      (cond
        (re-matches #"\d+h\d+m?" s)
        (let [[_ h m] (re-matches #"(\d+)h(\d+)m?" s)]
          (+ (* (parse-long h) 3600) (* (parse-long m) 60)))

        (re-matches #"\d+h" s)
        (* (parse-long (str/replace s "h" "")) 3600)

        (re-matches #"\d+m" s)
        (* (parse-long (str/replace s "m" "")) 60)

        (re-matches #"\d+s" s)
        (parse-long (str/replace s "s" ""))

        (re-matches #"\d+" s)
        (parse-long s)))))

(defn date-str [d]
  (subs (str d) 0 10))

(defn date-now []
  (LocalDate/now))

(defn parse-relative-date [s]
  (when-let [[_ _ n p _ q] (re-matches #"now(-(\d+)(d|w|m|y))?(/(w|m|y))?" s)]
    {:offset-amount (some-> n parse-long)
     :offset-unit p
     :snap-unit q}))

(defn minus [date unit amount]
  (case unit
    "d" (.minusDays date amount)
    "w" (.minusWeeks date amount)
    "m" (.minusMonths date amount)
    "y" (.minusYears date amount)))

(defn snap-down [local-date unit]
  (case unit
    "w" (.with local-date DayOfWeek/MONDAY)
    "m" (.withDayOfMonth local-date 1)
    "y" (.withDayOfYear local-date 1)))

(defn snap-up [local-date unit]
  (case unit
    "w" (.with local-date DayOfWeek/SUNDAY)
    "m" (.with local-date (TemporalAdjusters/lastDayOfMonth))
    "y" (.with local-date (TemporalAdjusters/lastDayOfYear))))

(defn parse-moment [s snap-fn]
  (when s
    (let [s (str/trim s)
          m (parse-relative-date s)]
      (str
       (cond
         (re-matches #"\d\d\d\d-\d\d-\d\d" s) s
         m (cond-> (date-now)
             (:offset-unit m) (minus (:offset-unit m) (:offset-amount m))
             (:snap-unit m) (snap-fn (:snap-unit m))))))))

(defn parse-from [s]
  (parse-moment s snap-down))

(defn parse-to [s]
  (parse-moment s snap-up))

(defn expand-range [{:keys [range] :as opts}]
  (case range
    "this-week" (assoc opts :from (parse-from "now/w") :to (parse-to "now/w"))
    "this-month" (assoc opts :from (parse-from "now/m") :to (parse-to "now/m"))
    "this-year" (assoc opts :from (parse-from "now/y") :to (parse-to "now/y"))

    "previous-week" (assoc opts :from (parse-from "now-1w/w") :to (parse-to "now-1w/w"))
    "previous-month" (assoc opts :from (parse-from "now-1m/m") :to (parse-to "now-1m/m"))
    "previous-year" (assoc opts :from (parse-from "now-1y/y") :to (parse-to "now-1y/y"))
    opts))

(defn parse-distance [s]
  (when s
    (cond
      (str/ends-with? s "k") (int (* 1000 (parse-double (subs s 0 (dec (count s))))))
      :else (parse-long s))))

(defn ->kebab-case-keyword [m]
  (walk/postwalk (fn [node]
                   (cond
                     (map? node) (medley/map-keys csk/->kebab-case node)
                     :else node))
                 m))

(defn help-requested [args spec]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println (cli/format-opts {:spec spec}))
    true))

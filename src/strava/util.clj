(ns strava.util
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [medley.core :as medley]
            [strava.me :as me])
  (:import (java.time
            LocalDate)))

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
  (java.time.LocalDate/now))

(defn date-now-minus [n]
  (.minusDays (date-now) n))

(defn first-day-of-week []
  (let [d (java.time.LocalDate/now)]
    (.minusDays d (dec (.getValue (.getDayOfWeek d))))))

(defn last-day-of-week []
  (let [d (java.time.LocalDate/now)]
    (.plusDays d (- 7 (.getValue (.getDayOfWeek d))))))

(defn last-day-of-month []
  (let [d (java.time.LocalDate/now)]
    (.withDayOfMonth d (.lengthOfMonth d))))

(defn first-day-of-month []
  (let [d (java.time.LocalDate/now)]
    (.withDayOfMonth d 1)))

(defn first-day-of-year []
  (let [d (LocalDate/now)]
    (.withDayOfYear d 1)))

(defn last-day-of-year []
  (let [d (LocalDate/now)]
    (.withDayOfYear d (.lengthOfYear d))))

(defn parse-relative-date [s]
  (when-let [[_ _ n p _ q] (re-matches #"now(-(\d+)(w|m|y))?(/(w|m|y))?" s)]
    {:offset-amount (some-> n parse-long)
     :offset-unit p
     :range-unit q}))

(defn parse-from [s]
  (when s
    (let [s (str/trim s)
          m (parse-relative-date s)]
      (str
       (cond
         (= "now" s) (date-now)
         (re-matches #"\d\d\d\d-\d\d-\d\d" s) s
         (:range-unit m) (let [d (case (:range-unit m)
                                   "w" (first-day-of-week)
                                   "m" (first-day-of-month)
                                   "y" (first-day-of-year))]
                           (if (:offset-unit m)
                             (case (:offset-unit m)
                               "w" (.minusWeeks d (:offset-amount m))
                               "m" (.minusMonths d (:offset-amount m))
                               "y" (.minusYears d (:offset-amount m)))
                             d)))))))

(defn parse-to [s]
  (when s
    (let [s (str/trim s)
          m (parse-relative-date s)]
      (str
       (cond
         (= "now" s) (date-now)
         (re-matches #"\d\d\d\d-\d\d-\d\d" s) s
         (:range-unit m) (let [d (case (:range-unit m)
                                   "w" (last-day-of-week)
                                   "m" (last-day-of-month)
                                   "y" (last-day-of-year))]
                           (if (:offset-unit m)
                             (case (:offset-unit m)
                               "w" (.minusWeeks d (:offset-amount m))
                               "m" (.minusMonths d (:offset-amount m))
                               "y" (.minusYears d (:offset-amount m)))
                             d)))))))

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

(ns strava.util
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [medley.core :as medley]
            [strava.me :as me]))

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

(defn parse-distance [s]
  (when s
    (cond
      (str/ends-with? s "k") (* 1000 (parse-long (subs s 0 (dec (count s)))))
      :else (parse-long s))))

(defn ->kebab-case-keyword [m]
  (walk/postwalk (fn [node]
                   (cond
                     (map? node) (medley/map-keys csk/->kebab-case node)
                     :else node))
                 m))

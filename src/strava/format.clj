(ns strava.format
  (:require [clojure.string :as str]))

(defn pace->str [seconds]
  (if seconds
    (format "%2d:%02d" (quot seconds 60) (mod seconds 60))
    (format "%5s" "-")))

(defn at->str [seconds]
  (when seconds
    (let [h (quot seconds 3600)
          m (rem (quot seconds 60) 60)
          s (rem seconds 60)]
      (format "%02d:%02d:%02d" h m s))))

(defn duration->str [seconds]
  (when seconds
    (let [h (quot seconds 3600)
          m (rem (quot seconds 60) 60)
          s (rem seconds 60)]
      (format "%9s"
              (cond
                (pos? h) (format "%d:%02d:%02d" h m s)
                :else (format "%d:%02d" m s))))))

(defn epoch->str [seconds]
  (->
   (java.time.LocalDateTime/ofInstant (java.time.Instant/ofEpochSecond seconds)
                                      (java.time.ZoneId/of "Europe/Amsterdam"))
   str
   (subs 0 16)
   (str/replace "T" " ")))

(defn fmt-int [w]
  #(format (str "%" w "s") (if % (long %) "-")))

(defn fmt-double [width decimals]
  #(if %
     (format (str "%" width "." decimals "f") (double %))
     (format (str "%" width "s") "-")))

(def formats {:id          ["id" (fmt-int 10)]
              :name        ["name" #(subs % 0 (min (count %) 30))]
              :at          ["at" at->str]
              :from        ["from" at->str]
              :to          ["to" at->str]
              :timestamp   ["ts" epoch->str]
              :duration    ["duration" duration->str]
              :moving      ["moving" duration->str]
              :elapsed     ["elapsed" duration->str]
              :pace        ["pace" pace->str]
              :distance    ["dist" #(some-> % (* 0.001) ((fmt-double 5 3)))]
              :covered     ["cov" #(some-> % (* 0.001) ((fmt-double 5 3)))]
              :cadence     ["cad" #(some-> % (* 2) ((fmt-int 3)))]
              :step-length ["sl" (fmt-int 3)]
              :kmph        ["km/h" (fmt-double 3 1)]
              :speed       ["m/s" (fmt-double 5 2)]
              :heart-rate  ["hr" (fmt-int 3)]
              :ef          ["EF" (fmt-double 5 3)]
              :efr         ["ef%" #(some-> % (* 100) ((fmt-double 4 1)))]})

(defn default-format-fn [v]
  (cond
    (float? v) (format "%5.3f" v)
    (double? v) (format "%5.3f" v)
    (int? v) (format "%5d" v)
    :else (format "%s" (if v v "-"))))

(defn format-fn [k]
  (get-in formats [k 1] default-format-fn))

(defn header [k]
  (get-in formats [k 0] (name k)))

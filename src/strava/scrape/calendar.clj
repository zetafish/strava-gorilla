(ns strava.scrape.calendar
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [strava.scrape.session :as session]))

(defn calendar-url [year]
  (format "https://www.strava.com/athlete/calendar/%d" year))

(defn fetch-year-html [year]
  (session/GET (calendar-url year)))

(def ^:private training-calendar-re
  #"(?s)var\s+trainingCalendar\s*=\s*(\{.*?\});")

(defn extract-training-calendar [html]
  (if-let [m (re-find training-calendar-re html)]
    (json/parse-string (second m))
    (throw (ex-info "trainingCalendar blob not found on page" {}))))

(defn- parse-km [s]
  (when (string? s)
    (let [digits (str/replace s #"[^\d.]" "")]
      (when (seq digits)
        (Double/parseDouble digits)))))

(def ^:private month-num
  {"Jan" 1 "Feb" 2 "Mar" 3 "Apr" 4 "May" 5 "Jun" 6
   "Jul" 7 "Aug" 8 "Sep" 9 "Oct" 10 "Nov" 11 "Dec" 12})

(defn month->activities [year month-key month-blob]
  (let [mn (or (month-num month-key)
               (get-in month-blob ["calendar" "month"]))
        days (get-in month-blob ["days" "values_by_day"])]
    (for [[day-str day-blob] days
          :let [acts (get day-blob "activities")]
          :when (sequential? acts)
          act acts]
      {:id (get act "id")
       :name (get act "name")
       :type (get act "type")
       :date (format "%04d-%02d-%02d" year mn (Long/parseLong day-str))
       :day-distance-km (parse-km (get day-blob "distance"))})))

(defn parse-year [year html]
  (let [cal (extract-training-calendar html)
        months (get cal "months")]
    (mapcat (fn [[k v]] (month->activities year k v)) months)))

(defn fetch-year [year]
  (parse-year year (fetch-year-html year)))

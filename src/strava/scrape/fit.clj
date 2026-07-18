(ns strava.scrape.fit
  (:require [clojure.java.io :as io]
            [strava.scrape.session :as session]))

(defn export-url [id]
  (format "https://www.strava.com/activities/%s/export_original" id))

(defn download
  "Download the original .fit file for an activity to `file`.
   Uses the session cookie — no OAuth rate limit."
  [id file]
  (let [bytes (session/GET (export-url id) :as :bytes)]
    (io/copy bytes (io/file file))
    file))

(ns strava.scrape.activity
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [strava.scrape.session :as session]))

(defn activity-url [id]
  (format "https://www.strava.com/activities/%s" id))

(defn fetch-html [id]
  (session/GET (activity-url id)))

(def ^:private activity-set-re
  #"(?s)pageView\.activity\(\)\.set\(\{([^}]*?)\}\)")

(def ^:private lightbox-re
  #"(?s)var\s+lightboxData\s*=\s*(\{[^}]*\})")

(def ^:private mbr-re
  #"\.mbr\(\s*(\[\[[^\]]+\],\s*\[[^\]]+\]\])\s*\)")

(def ^:private static-map-re
  #"\.staticMapUrl\(\s*'([^']+)'\s*\)")

(defn- js-obj->json [s]
  ;; Convert a JS object body (bare keys, single-quoted strings) into JSON.
  ;; Only handles the shapes Strava emits inside pageView.activity().set and lightboxData.
  (-> (str "{" s "}")
      (str/replace #"([{,]\s*)([A-Za-z_][A-Za-z0-9_]*)(\s*:)" "$1\"$2\"$3")
      (str/replace #"'([^']*)'" "\"$1\"")))

(defn- parse-js-obj [body]
  (try
    (json/parse-string (js-obj->json body))
    (catch Exception _ nil)))

(defn- find-activity-set [html]
  ;; Multiple pageView.activity().set(...) calls exist on the page; the one
  ;; with the payload we want contains "distance:". Merge all just in case.
  (->> (re-seq activity-set-re html)
       (map (comp parse-js-obj second))
       (filter map?)
       (reduce merge {})))

(defn- find-lightbox [html]
  (some-> (re-find lightbox-re html) second
          (str/replace #"^\{" "") (as-> b (parse-js-obj b))))

(defn- find-mbr [html]
  (when-let [m (re-find mbr-re html)]
    (try (json/parse-string (second m)) (catch Exception _ nil))))

(defn- find-static-map [html]
  (some-> (re-find static-map-re html) second))

(defn parse
  "Extract fields available directly from the /activities/<id> HTML.
   Returns a map with (any present of):
   :distance :moving_time :elev_gain :calories :workout_type
   :average_heartrate :average_speed :average_cadence :trainer
   :name :sport_type :mbr :static_map_url

   NOTE: max_heartrate, has_heartrate, summary_polyline, per-second
   streams are NOT in the HTML — they load from separate stream endpoints."
  [html]
  (let [act (find-activity-set html)
        lb  (find-lightbox html)
        mbr (find-mbr html)
        smap (find-static-map html)]
    (cond-> {}
      (get act "distance")      (assoc :distance (get act "distance"))
      (get act "moving_time")   (assoc :moving_time (get act "moving_time"))
      (get act "elev_gain")     (assoc :elev_gain (get act "elev_gain"))
      (get act "calories")      (assoc :calories (get act "calories"))
      (contains? act "workout_type") (assoc :workout_type (get act "workout_type"))
      (get act "avg_hr")        (assoc :average_heartrate (get act "avg_hr"))
      (get act "avg_speed")     (assoc :average_speed (get act "avg_speed"))
      (get act "avg_cadence")   (assoc :average_cadence (get act "avg_cadence"))
      (contains? act "trainer") (assoc :trainer (get act "trainer"))
      (get lb "title")          (assoc :name (get lb "title"))
      (get lb "activity_type")  (assoc :sport_type (get lb "activity_type"))
      mbr                       (assoc :mbr mbr)
      smap                      (assoc :static_map_url smap))))

(defn fetch [id]
  (parse (fetch-html id)))

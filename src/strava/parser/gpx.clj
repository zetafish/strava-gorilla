(ns strava.parser.gpx
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]))

(defn- haversine [lat1 lon1 lat2 lon2]
  (let [R 6371000
        dlat (Math/toRadians (- lat2 lat1))
        dlon (Math/toRadians (- lon2 lon1))
        a (+ (* (Math/sin (/ dlat 2)) (Math/sin (/ dlat 2)))
             (* (Math/cos (Math/toRadians lat1))
                (Math/cos (Math/toRadians lat2))
                (Math/sin (/ dlon 2)) (Math/sin (/ dlon 2))))]
    (* R 2 (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1 a))))))

(defn- local-name [tag]
  (when tag
    (let [s (name tag)]
      (keyword (or (second (re-matches #".*:(.*)" s)) s)))))

(defn- child-text [el tag]
  (some #(when (= tag (local-name (:tag %)))
           (first (:content %)))
        (:content el)))

(defn- parse-extensions [el]
  (reduce (fn [m child]
            (if-let [k (local-name (:tag child))]
              (case k
                :TrackPointExtension
                (if-let [hr (child-text child :hr)]
                  (assoc m :hr hr)
                  m)
                (let [v (first (:content child))]
                  (cond-> m
                    (string? v) (assoc k v))))
              m))
          {}
          (:content el)))

(defn- parse-trkpt [el]
  (let [lat (parse-double (get-in el [:attrs :lat]))
        lon (parse-double (get-in el [:attrs :lon]))
        ext-el (first (filter #(= :extensions (local-name (:tag %))) (:content el)))
        exts (when ext-el (parse-extensions ext-el))
        time-str (child-text el :time)
        ts (when time-str
             (.getEpochSecond (java.time.Instant/parse time-str)))]
    (cond-> {:position_lat lat
             :position_long lon
             :timestamp ts}
      (:speed exts) (assoc :speed (parse-double (:speed exts)))
      (:distance exts) (assoc :distance (parse-double (:distance exts)))
      (:altitude exts) (assoc :altitude (parse-double (:altitude exts)))
      (:cadence exts) (assoc :cadence (parse-long (:cadence exts)))
      (:hr exts) (assoc :heart_rate (parse-long (:hr exts))))))

(defn- enrich-with-distance [trkpts]
  (loop [acc []
         dist 0.0
         prev nil
         [pt & more] trkpts]
    (if-not pt
      acc
      (let [d (if prev
                (haversine (:position_lat prev) (:position_long prev)
                           (:position_lat pt) (:position_long pt))
                0.0)
            dist (+ dist d)
            dt (if prev (- (:timestamp pt) (:timestamp prev)) 1)
            speed (if (pos? dt) (/ d dt) 0.0)]
        (recur (conj acc (assoc pt :distance dist :speed speed))
               dist pt more)))))

(defn records [gpx-path]
  (with-open [r (io/reader gpx-path)]
    (let [root (xml/parse r)
          trkpts (->> (:content root)
                      (mapcat :content)
                      (mapcat :content)
                      (filter #(= :trkpt (local-name (:tag %))))
                      (mapv parse-trkpt))
          trkpts (->> trkpts
                      (remove (comp nil? :timestamp))
                      (map #(cond-> %
                              (nil? (:distance %)) (dissoc :speed))))
          has-distance? (:distance (first trkpts))
          trkpts (if has-distance?
                   (remove (comp nil? :speed) trkpts)
                   (enrich-with-distance trkpts))
          start-ts (:timestamp (first trkpts))]
      (mapv #(assoc % :at (- (:timestamp %) start-ts)) trkpts))))

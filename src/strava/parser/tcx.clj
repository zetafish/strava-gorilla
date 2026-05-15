(ns strava.parser.tcx
  (:require [clojure.data.xml :as xml]
            [clojure.string :as str]))

(defn- local-name [tag]
  (when tag
    (let [s (name tag)]
      (keyword (or (second (re-matches #".*:(.*)" s)) s)))))

(defn- child-el [el tag]
  (first (filter #(= tag (local-name (:tag %))) (:content el))))

(defn- child-text [el tag]
  (some-> (child-el el tag) :content first))

(defn- parse-trackpoint [el]
  (let [time-str (child-text el :Time)
        ts (when time-str (.getEpochSecond (java.time.Instant/parse time-str)))
        pos (child-el el :Position)
        lat (some-> (child-text pos :LatitudeDegrees) parse-double)
        lon (some-> (child-text pos :LongitudeDegrees) parse-double)
        alt (some-> (child-text el :AltitudeMeters) parse-double)
        cad (some-> (child-text el :Cadence) parse-long)
        hr (some-> (child-el el :HeartRateBpm) (child-text :Value) parse-long)
        dist (some-> (child-text el :DistanceMeters) parse-double)
        ext (child-el el :Extensions)
        tpx (when ext (child-el ext :TPX))
        speed (some-> (when tpx (child-text tpx :Speed)) parse-double)]
    (cond-> {:timestamp ts}
      lat (assoc :position_lat lat)
      lon (assoc :position_long lon)
      alt (assoc :altitude alt)
      cad (assoc :cadence cad)
      hr (assoc :heart_rate hr)
      dist (assoc :distance dist)
      speed (assoc :speed speed))))

(defn records [tcx-path]
  (let [root (xml/parse-str (str/trim (slurp tcx-path)))
        trkpts (->> (:content root)
                    (mapcat :content)
                    (mapcat :content)
                    (filter #(= :Lap (local-name (:tag %))))
                    (mapcat :content)
                    (filter #(= :Track (local-name (:tag %))))
                    (mapcat :content)
                    (filter #(= :Trackpoint (local-name (:tag %))))
                    (mapv parse-trackpoint))
        trkpts (->> trkpts
                    (remove (comp nil? :timestamp))
                    (remove (comp nil? :speed)))
        trkpts (if (:distance (first trkpts))
                 trkpts
                 (reductions (fn [prev pt]
                               (let [dt (- (:timestamp pt) (:timestamp prev))
                                     d (* (:speed pt) dt)]
                                 (assoc pt :distance (+ (:distance prev) d))))
                             (assoc (first trkpts) :distance 0.0)
                             (rest trkpts)))
        start-ts (:timestamp (first trkpts))]
    (mapv #(assoc % :at (- (:timestamp %) start-ts)) trkpts)))

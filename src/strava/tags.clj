(ns strava.tags
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]))

(def tags-file ".tags.edn")

(defn load-tags []
  (if (fs/exists? tags-file)
    (edn/read-string (slurp tags-file))
    {}))

(defn save-tags [tags]
  (spit tags-file (with-out-str (pprint/pprint tags))))

(defn auto-tags [activity]
  (let [km (/ (:distance activity 0) 1000.0)
        dist-tag (cond
                   (> km 43) :ultra
                   (>= km 20) :long
                   (>= km 10) :medium
                   :else :short)
        race? (= 1 (:workout_type activity))]
    (cond-> #{dist-tag}
      race? (conj :race))))

(defn all-tags [activity-id activity]
  (let [manual (get (load-tags) activity-id #{})]
    (into manual (auto-tags activity))))

(defn add-tags [activity-id new-tags]
  (let [tags (load-tags)
        existing (get tags activity-id #{})
        updated (assoc tags activity-id (into existing new-tags))]
    (save-tags updated)))

(defn remove-tags [activity-id rm-tags]
  (let [tags (load-tags)
        existing (get tags activity-id #{})
        remaining (reduce disj existing rm-tags)
        updated (if (empty? remaining)
                  (dissoc tags activity-id)
                  (assoc tags activity-id remaining))]
    (save-tags updated)))

(defn load-all-activities []
  (->> (fs/list-dir ".activities" "*.json")
       (mapcat #(json/parse-string (slurp (str %)) true))))

(defn find-by-tag [tag]
  (let [manual-tags (load-tags)
        activities (load-all-activities)]
    (filter (fn [a]
              (let [id (:id a)
                    tags (into (get manual-tags id #{}) (auto-tags a))]
                (contains? tags tag)))
            activities)))

(defn needs-review []
  (let [manual-tags (load-tags)
        activities (load-all-activities)]
    (filter (fn [a]
              (let [id (:id a)
                    manual (get manual-tags id #{})]
                (and (> (/ (:distance a 0) 1000.0) 43)
                     (not (contains? manual :race))
                     (not= 1 (:workout_type a)))))
            activities)))

(ns strava.load
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [strava.tags :as tags]))

(def athlete-file ".athlete.edn")

(def defaults {:resting-hr 50 :max-hr 185})

(defn load-athlete []
  (merge defaults
         (when (fs/exists? athlete-file)
           (edn/read-string (slurp athlete-file)))))

(defn trimp
  [{:keys [average_heartrate moving_time]} {:keys [resting-hr max-hr]}]
  (when (and average_heartrate moving_time
             (> average_heartrate resting-hr)
             (> max-hr resting-hr))
    (let [duration-min (/ moving_time 60.0)
          hr-frac (/ (- average_heartrate resting-hr)
                     (- max-hr resting-hr))
          hr-frac (min 1.0 (max 0.0 hr-frac))]
      (* duration-min hr-frac 0.64 (Math/exp (* 1.92 hr-frac))))))

(defn activity-date [activity]
  (some-> (:start_date_local activity) (subs 0 10)))

(defn daily-loads [activities athlete]
  (->> activities
       (filter :has_heartrate)
       (filter #(= "Run" (:sport_type %)))
       (group-by activity-date)
       (map (fn [[date acts]]
              {:date date
               :trimp (reduce + (keep #(trimp % athlete) acts))
               :count (count acts)
               :distance (reduce + (map #(/ (:distance % 0) 1000.0) acts))}))
       (sort-by :date)))

(defn fill-gaps [daily-loads]
  (when (seq daily-loads)
    (let [parse (fn [s] (java.time.LocalDate/parse s))
          start (parse (:date (first daily-loads)))
          end (parse (:date (last daily-loads)))
          load-by-date (into {} (map (juxt :date identity)) daily-loads)]
      (loop [d start acc []]
        (if (.isAfter d end)
          acc
          (let [ds (str d)]
            (recur (.plusDays d 1)
                   (conj acc (or (get load-by-date ds)
                                 {:date ds :trimp 0 :count 0 :distance 0})))))))))

(defn ewma [time-constant values]
  (let [alpha (- 1.0 (Math/exp (/ -1.0 time-constant)))]
    (rest (reductions (fn [prev v] (+ prev (* alpha (- v prev)))) 0.0 values))))

(defn compute-load [activities athlete {:keys [weeks from to]}]
  (let [display-from (or from (str (.minusWeeks (java.time.LocalDate/now) weeks)))
        display-to (or to (str (java.time.LocalDate/now)))
        seed-from (str (.minusDays (java.time.LocalDate/parse display-from) 42))
        all-daily (daily-loads activities athlete)
        filtered (filter #(and (pos? (compare (:date %) seed-from))
                               (not (pos? (compare (:date %) display-to)))) all-daily)
        filled (fill-gaps filtered)
        trimps (map :trimp filled)
        atl (ewma 7 trimps)
        ctl (ewma 42 trimps)]
    (->> (map (fn [day a c]
                (assoc day :atl a :ctl c :tsb (- c a)))
              filled atl ctl)
         (filter #(and (not (neg? (compare (:date %) display-from)))
                       (not (pos? (compare (:date %) display-to))))))))

(ns strava.track)

(defn trim-head [track]
  (drop-while (comp zero? :speed) track))

(defn trim-tail [track]
  (->> track
       reverse
       (drop-while (comp zero? :speed))
       reverse))

(defn remove-stationary [track]
  (remove (comp zero? :speed) track))

(defn epoch->instant [n]
  (java.time.Instant/ofEpochSecond n))

(defn rebase-at [track]
  (let [base (:at (first track))]
    (map #(update % :at - base) track)))

(defn double-cadence [track]
  (map #(update % :cadence (fn [c]
                             (when c
                               (* 2 c))))
       track))

(defn avg [k coll]
  (let [vals (keep k coll)]
    (when (seq vals)
      (double (/ (reduce + vals) (count vals))))))

(defn pace [{:keys [speed]}]
  (when (and speed (pos? speed))
    (int (/ 3600 (* 3.6 speed)))))

(defn kmph [{:keys [speed]}]
  (when speed
    (* 3.6 speed)))

(defn efficiency [{:keys [speed heart_rate]}]
  (when (and speed heart_rate (pos? heart_rate))
    (* 60 (/ speed heart_rate))))

(defn enrich [m]
  (-> m
      (assoc :pace (pace m))
      (assoc :kmph (kmph m))
      (assoc :ef (efficiency m))
      (assoc :date (subs (str (java.time.Instant/ofEpochSecond (:timestamp m)))
                         0 10))))

(defn agg [coll]
  (case (count coll)
    0 nil
    1 (enrich (first coll))
    (let [duration (- (:at (last coll)) (:at (first coll)))
          dist-covered (- (:distance (last coll)) (:distance (first coll)))]
      (enrich {:pts (count coll)
               :at (:at (first coll))
               :timestamp (:timestamp (first coll))
               :duration duration
               :distance (:distance (last coll))
               :step_length (some-> (avg :step_length coll) int)
               :heart_rate (some-> (avg :heart_rate coll) int)
               :cadence (some-> (avg :cadence coll) int (* 2))
               :speed (/ dist-covered duration)}))))

(defn split-evenly [n coll]
  (let [v (vec coll)
        c (count v)
        q (quot c n)
        r (rem c n)
        sizes (map + (repeat n q) (concat (repeat r 1) (repeat 0)))
        edges (reductions + 0 sizes)]
    (map (fn [start end]
           (if (< end (count v))
             (subvec v start (inc end))
             (subvec v start end)))
         edges
         (rest edges))))

(defn- split-by-key [key window track]
  (let [segments (partition-by #(quot (get % key) window) track)]
    (->> (map (fn [s1 s2]
                (concat s1 [(first s2)]))
              segments
              (concat (rest segments) [nil]))
         (map #(keep identity %))
         (remove #(= 1 (count %))))))

(defmulti splits :strategy)

(defmethod splits :duration [{:keys [duration]} track]
  (map agg (split-by-key :at duration track)))

(defmethod splits :distance [{:keys [distance]} track]
  (map agg (split-by-key :distance distance track)))

(defmethod splits :evenly [{:keys [evenly]} track]
  (map agg (split-evenly evenly track)))

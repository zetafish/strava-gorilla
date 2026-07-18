(ns strava.format)

(defn pace->str [seconds]
  (when seconds
    (format "%2d:%02d" (quot seconds 60) (mod seconds 60))))

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
      (cond
        (pos? h) (format "%2d:%02d:%02d" h m s)
        :else (format "%5d:%02d" m s)))))

(defn epoch->str [seconds]
  (str (java.time.Instant/ofEpochSecond seconds)))

(def formats {:id          ["id" (partial format "%10d")]
              :name        ["name" #(subs % 0 (min (count %) 30))]
              :at          ["at" at->str]
              :timestamp   ["ts" epoch->str]
              :duration    ["duration" duration->str]
              :distance    ["dist" #(->> % (* 0.001) (format "%5.1f"))]
              :heart_rate  ["hr" (partial format "%d")]
              :step_length ["sl" (partial format "%d")]
              :cadence     ["cad" (partial format "%d")]
              :pace        ["pace" pace->str]
              :kmph        ["km/h" (partial format "%3.1f")]
              :speed       ["m/s" (partial format "%3.1f")]
              :ef          ["ef" (partial format "%5.3f")]})

(defn default-format-fn [v]
  (cond
    (float? v) (format "%5.3f" v)
    (double? v) (format "%5.3f" v)
    (int? v) (format "%5d" v)
    :else (format "%s" v)))

(defn format-fn [k]
  (get-in formats [k 1] default-format-fn))

(defn header [k]
  (get-in formats [k 0] (name k)))

(comment
  (def format-fn {:kmph (partial format "%3.1f")
                  :speed (partial format "%3.1.f")


                  :ef (partial format "%5.3f")
                  :ef_si (partial format "%5.3f")
                  :ef_metric (partial format "%5.3f")

                  :distance #(some->> % (* 0.001) (format "%5.1f"))
                  :step_length (partial format "%3d")
                  :pts (partial format "%4d")
                  :heart_rate (partial format "%3d")
                  :cadence (partial format "%3d")
                  :duration fmt/duration->str

                  :drift #(if % (format "%+.1f%%" %) "-")
                  :split #(if % (format "%.2f" %) "-")

                  :score #(if % (format "%.0fm" %) "-")
                  :ef-pct #(if % (format "%.0f%%" %) "-")
                  :name #(let [n (or % "")] (subs n 0 (min (count n) 30)))
                  :date #(some-> % (subs 0 10))}))

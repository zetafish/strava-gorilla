(ns strava.table
  (:require [clojure.string :as str]
            [strava.format :as fmt]))

(def default-formats {:kmph "%3.1f"
                      :speed "%3.1f"
                      :ef "%5.3f"
                      :ef_si "%5.3f"
                      :ef_metric "%5.3f"
                      :distance "%d"
                      :step_length "%d"
                      :pts "%d"
                      :heart_rate "%d"
                      :cadence "%d"})

(def format-fn {:kmph (partial format "%3.1f")
                :speed (partial format "%3.1.f")
                :pace fmt/pace->str

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
                :date #(some-> % (subs 0 10))})

(defn default-format-fn [v]
  ;; (println v)
  (cond
    (float? v) (format "%5.3f" v)
    (double? v) (format "%5.3f" v)
    (int? v) (format "%5d" v)
    :else (format "%s" v)))

(defn max-widths [grid]
  (->> (range (count (first grid)))
       (map (fn [i] (map #(count (nth % i)) grid)))
       (map #(apply max %))))

(defn pad [s n]
  (let [spaces (str/join (repeat (max 0 (- n (count s))) " "))
        dir (if (and s (parse-double s)) :left :right)]
    (case dir
      :left (str spaces s)
      :right (str s spaces))))

(defn print-table [header keys data]
  (let [as-values (apply juxt keys)
        data-rows (->> data
                       (map as-values)
                       (map #(map-indexed (fn [i v]
                                            (let [k (nth keys i)
                                                  f (format-fn k default-format-fn)]
                                              (f v))) %)))
        rows (concat [header] data-rows)
        widths (max-widths rows)
        table (->> rows
                   (map #(map-indexed (fn [i v]
                                        (pad v (nth widths i))) %))
                   (map #(str/join "  " %)))
        hline (str/join (repeat (count (first table)) "-"))]
    (println hline)
    (println (first table))
    (println hline)
    (run! println (rest table))
    (println hline)))

(comment (print-table ["AA" "BB"]
                      [:a :b]
                      ["%d" "%3.1f"]
                      [{:a 1 :b 99.2345}
                       {:a 9999 :b 3.1}]))

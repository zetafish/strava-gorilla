(ns strava.table
  (:require [clojure.string :as str]))

(def default-formats {:kmph "%3.1f"
                      :speed "%3.1f"
                      :ef_si "%5.3f"
                      :ef_metric "%5.3f"
                      :distance "%d"
                      :step_length "%d"
                      :pts "%d"
                      :heart_rate "%d"
                      :cadence "%d"})

(defn max-widths [grid]
  (->> (range (count (first grid)))
       (map (fn [i] (map #(count (nth % i)) grid)))
       (map #(apply max %))))

(defn pad [s n dir]
  (let [spaces (str/join (repeat (max 0 (- n (count s))) " "))]
    (case dir
      :left (str spaces s)
      :right (str s spaces))))

(defn print-table [header keys data]
  (let [get-fmt #(let [k (nth keys %)]
                   (get default-formats k "%s"))
        as-values (apply juxt keys)
        data-rows (->> data
                       (map as-values)
                       (map #(map-indexed (fn [i v]
                                            (let [f (get-fmt i)]
                                              (if (string? v)
                                                (str v)
                                                (format f v)))) %)))
        rows (concat [header] data-rows)
        widths (max-widths rows)
        table (->> rows
                   (map #(map-indexed (fn [i v]
                                        (let [f (get-fmt i)
                                              dir (cond
                                                    (str/includes? f "f") :left
                                                    (str/includes? f "d") :left
                                                    :else :right)]
                                          (pad v (nth widths i) dir))) %))
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

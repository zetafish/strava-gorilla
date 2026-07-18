(ns strava.table
  (:require [clojure.string :as str]
            [strava.format :as fmt]))

(defn max-widths [grid]
  (->> (range (count (first grid)))
       (map (fn [i] (map #(count (nth % i)) grid)))
       (map #(apply max %))))

(defn pad [s n dir]
  (let [spaces (str/join (repeat (max 0 (- n (count s))) " "))]
    (case dir
      :left (str spaces s)
      :right (str s spaces))))

(defn print-table
  ([keys data] (print-table (map fmt/header keys) keys data))
  ([header keys data]
   (let [data-rows (map (fn [row]
                          (mapv (fn [k v] ((fmt/format-fn k) v))
                                keys
                                row))
                        (map (apply juxt keys) data))
         rows (concat [header] data-rows)
         widths (max-widths rows)
         dirs (map #(if (parse-double %) :left :right) (first data-rows))
         table (->> (concat [(map pad header widths (repeat :right))]
                            (->> data-rows
                                 (map #(map pad % widths dirs))))
                    (map #(str/join "  " %)))
         hline (str/join (repeat (count (first table)) "-"))]
     (println hline)
     (println (first table))
     (println hline)
     (run! println (rest table))
     (println hline))))

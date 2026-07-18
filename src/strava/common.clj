(ns strava.common)

(defn split-into
  "Split coll into n parts of roughly the same size"
  [n coll]
  (let [c (count coll)
        q (quot c n)
        r (rem c n)
        sizes (map + (repeat n q) (concat (repeat r 1) (repeat 0)))]
    (loop [result [] coll coll sizes sizes]
      (if-not (seq sizes)
        result
        (let [[a b] (split-at (first sizes) coll)]
          (recur (conj result (vec a)) b (rest sizes)))))))

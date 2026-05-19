(ns strava.geo)

(defn decode-polyline [s]
  (let [chars (vec s)
        len (count chars)]
    (loop [i 0 lat 0 lng 0 result (transient [])]
      (if (>= i len)
        (persistent! result)
        (let [[i2 dlat] (loop [i i shift 0 acc 0]
                          (let [b (- (int (nth chars i)) 63)
                                acc (bit-or acc (bit-shift-left (bit-and b 0x1f) shift))
                                shift (+ shift 5)]
                            (if (>= b 32)
                              (recur (inc i) shift acc)
                              [(inc i) (if (pos? (bit-and acc 1))
                                         (- (bit-shift-right (inc acc) 1))
                                         (bit-shift-right acc 1))])))
              lat (+ lat dlat)
              [i3 dlng] (loop [i i2 shift 0 acc 0]
                          (let [b (- (int (nth chars i)) 63)
                                acc (bit-or acc (bit-shift-left (bit-and b 0x1f) shift))
                                shift (+ shift 5)]
                            (if (>= b 32)
                              (recur (inc i) shift acc)
                              [(inc i) (if (pos? (bit-and acc 1))
                                         (- (bit-shift-right (inc acc) 1))
                                         (bit-shift-right acc 1))])))
              lng (+ lng dlng)]
          (recur i3 lat lng (conj! result [(/ lat 1e5) (/ lng 1e5)])))))))

(defn haversine [[lat1 lng1] [lat2 lng2]]
  (let [r 6371000
        dlat (Math/toRadians (- lat2 lat1))
        dlng (Math/toRadians (- lng2 lng1))
        lat1r (Math/toRadians lat1)
        lat2r (Math/toRadians lat2)
        a (+ (* (Math/sin (/ dlat 2)) (Math/sin (/ dlat 2)))
             (* (Math/cos lat1r) (Math/cos lat2r)
                (Math/sin (/ dlng 2)) (Math/sin (/ dlng 2))))]
    (* r 2 (Math/asin (Math/sqrt a)))))

(defn normalize-polyline [points n]
  (let [cnt (count points)]
    (if (<= cnt n)
      (vec points)
      (mapv #(nth points (int (* (/ % (dec n)) (dec cnt)))) (range n)))))

(defn avg-min-distance [pts-a pts-b]
  (if (or (empty? pts-a) (empty? pts-b))
    Double/MAX_VALUE
    (let [sum (reduce (fn [acc pa]
                        (+ acc (reduce (fn [mn pb] (min mn (haversine pa pb)))
                                       Double/MAX_VALUE pts-b)))
                      0.0 pts-a)]
      (/ sum (count pts-a)))))

(defn route-similarity [poly-a poly-b]
  (let [n 50
        a (normalize-polyline poly-a n)
        b (normalize-polyline poly-b n)]
    (max (avg-min-distance a b)
         (avg-min-distance b a))))

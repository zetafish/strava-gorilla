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

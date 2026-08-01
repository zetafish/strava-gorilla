(ns strava.chart
  "Namespace with fns to draw ascii and svg charts"
  (:require [clojure.string :as str]))

(defn- nice-step [raw-step]
  (let [pow (Math/pow 10 (Math/floor (Math/log10 raw-step)))
        n (/ raw-step pow)
        nice (cond (< n 1.5) 1
                   (< n 3)   2
                   (< n 7)   5
                   :else     10)]
    (* nice pow)))

(defn- nice-ticks [lo hi target]
  (let [span (- hi lo)
        step (nice-step (/ span (max 1 target)))
        start (* step (Math/floor (/ lo step)))
        end (* step (Math/ceil (/ hi step)))]
    (->> (iterate #(+ % step) start)
         (take-while #(<= % (+ end (* 0.5 step))))
         vec)))

(defn svg-line-chart
  "Render an SVG line chart of `metric` across `rows`.
  Options: :width :height :metric :file (default \".data/chart.svg\") :title
  Returns the file path written."
  [rows {:keys [width height metric file title]
         :or {width 900 height 300 file "scratch/chart.svg"}}]
  (let [values (vec (keep metric rows))
        n (count values)]
    (when (pos? n)
      (let [pad-l 55 pad-r 15 pad-t (if title 30 15) pad-b 30
            plot-w (- width pad-l pad-r)
            plot-h (- height pad-t pad-b)
            lo (apply min values)
            hi (apply max values)
            ticks (nice-ticks lo hi 5)
            y-lo (first ticks)
            y-hi (last ticks)
            y-span (max 1e-9 (- y-hi y-lo))
            x-of (fn [i] (+ pad-l (* (/ i (max 1 (dec n))) plot-w)))
            y-of (fn [v] (+ pad-t (* (- 1.0 (/ (- v y-lo) y-span)) plot-h)))
            points (->> values
                        (map-indexed (fn [i v] (format "%.1f,%.1f" (double (x-of i)) (double (y-of v)))))
                        (str/join " "))
            y-gridlines (for [t ticks
                              :let [y (y-of t)]]
                          (format "<line x1='%d' y1='%.1f' x2='%d' y2='%.1f' stroke='#e5e5e5'/><text x='%d' y='%.1f' font-size='11' text-anchor='end' fill='#555' font-family='monospace'>%s</text>"
                                  pad-l (double y) (- width pad-r) (double y)
                                  (- pad-l 6) (double (+ y 4))
                                  (if (== t (Math/floor t)) (format "%.0f" (double t)) (format "%.1f" (double t)))))
            x-tick-count 8
            x-labels (for [i (range (inc x-tick-count))
                           :let [idx (int (Math/round (double (* (/ i x-tick-count) (dec n)))))
                                 x (x-of idx)]]
                       (format "<text x='%.1f' y='%d' font-size='11' text-anchor='middle' fill='#555' font-family='monospace'>%d</text>"
                               (double x) (- height 10) idx))
            title-el (when title
                       (format "<text x='%d' y='20' font-size='14' fill='#222' font-family='monospace'>%s</text>"
                               pad-l title))
            svg (str "<svg xmlns='http://www.w3.org/2000/svg' width='" width "' height='" height "' viewBox='0 0 " width " " height "'>"
                     "<rect width='100%' height='100%' fill='white'/>"
                     (or title-el "")
                     (str/join "" y-gridlines)
                     (str/join "" x-labels)
                     (format "<rect x='%d' y='%d' width='%d' height='%d' fill='none' stroke='#999'/>"
                             pad-l pad-t plot-w plot-h)
                     (format "<polyline fill='none' stroke='#c33' stroke-width='1.5' points='%s'/>" points)
                     "</svg>")]
        (spit file svg)
        file))))

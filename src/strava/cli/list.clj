(ns strava.cli.list
  (:require [babashka.cli :as cli]
            [clojure.string :as str]
            [strava.repo :as repo]
            [strava.table :as table]
            [strava.util :as u]))

(def spec {:pattern {:alias :p :desc "Match part of the name"}
           :type {:desc "Activity type, e.g. Run, Walk, Hike, Ride"}
           :from {:desc "Activities since yyyy-mm-dd" :coerce u/parse-from}
           :to {:desc "Activities until yyyy-mm-dd" :coerce u/parse-to}
           :range {:desc "this-week, previous-month, this-year, ..."}
           :limit {:alias :n :coerce :int :desc "Show only the last N activities"}
           :help {:alias :h :coerce :boolean}})

(defn find-entries [{:keys [pattern type from to limit]}]
  (cond->> (sort-by (juxt :date :id) (repo/load-calendars))
    type (filter #(= (str/lower-case type) (str/lower-case (:type %))))
    pattern (filter #(str/includes? (str/lower-case (:name %)) (str/lower-case pattern)))
    from (filter #(<= 0 (compare (:date %) from)))
    to (filter #(<= 0 (compare to (:date %))))
    limit (take-last limit)))

(defn run [args]
  (let [opts (-> (cli/parse-opts args {:spec spec}) u/expand-range)]
    (if (:help opts)
      (println (cli/format-opts {:spec spec}))
      (table/print-table ["date" "id" "type" "day km" "name"]
                         [:date :id :type :day-km :title]
                         (map #(assoc % :day-km (some->> (:day-distance-km %) double (format "%.1f"))
                                      :title (:name %))
                              (find-entries opts))))))

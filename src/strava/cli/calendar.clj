(ns strava.cli.calendar
  (:require [babashka.cli :as cli]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def spec {:week {:coerce :string :desc "Show specific week (YYYY-MM-DD)"}
           :all {:coerce :boolean :desc "Show all weeks"}
           :summary {:coerce :boolean :desc "Show week-by-week summary"}
           :file {:coerce :string :default "coaching/balatonfured-2026.edn" :desc "Calendar EDN file"}
           :help {:alias :h :coerce :boolean}})

(def day-names ["Mon" "Tue" "Wed" "Thu" "Fri" "Sat" "Sun"])

(defn load-calendar [file]
  (edn/read-string (slurp file)))

(defn today []
  (str (java.time.LocalDate/now)))

(defn current-week-monday []
  (let [d (java.time.LocalDate/now)
        dow (.getValue (.getDayOfWeek d))]
    (str (.minusDays d (dec dow)))))

(defn format-type [t]
  (when t
    ({"easy" "Easy" "long" "Long" "tempo" "Tempo" "back-to-back" "B2B"
      "race" "RACE" "rest" "Rest" "walk" "Walk" "recovery" "Recovery"}
     (name t) (name t))))

(defn format-planned [{:keys [type km notes]}]
  (if-not type
    ""
    (let [parts [(format-type type)
                 (when (and km (pos? km)) (str km "km"))
                 (when notes (str "(" notes ")"))]]
      (str/join " " (remove nil? parts)))))

(defn format-actual [{:keys [km id notes]}]
  (cond
    (nil? km) ""
    :else (let [parts [(format "%.1fkm" (double km))
                       (when id (str "#" id))
                       (when notes (str "(" notes ")"))]]
            (str/join " " (remove nil? parts)))))

(defn day-index [date-str]
  (let [d (java.time.LocalDate/parse date-str)]
    (dec (.getValue (.getDayOfWeek d)))))

(defn format-date-short [date-str]
  (let [d (java.time.LocalDate/parse date-str)
        day-num (.getDayOfMonth d)
        dow (day-names (dec (.getValue (.getDayOfWeek d))))]
    (format "%s %02d" dow day-num)))

(defn planned-km [week]
  (->> (:days week)
       (map #(get-in % [:planned :km] 0))
       (reduce +)))

(defn actual-km [week]
  (->> (:days week)
       (map #(get-in % [:actual :km] 0))
       (reduce +)))

(defn print-week [week]
  (let [plan-total (planned-km week)
        act-total (actual-km week)
        today-str (today)]
    (println)
    (println (str "Week of " (:week-of week)
                  "  |  Target: " (:target-km week) " km"
                  (when (:notes week) (str "  |  " (:notes week)))))
    (println (str/join (repeat 80 "-")))
    (printf "%-8s  %-35s  %s%n" "Day" "Plan" "Actual")
    (println (str/join (repeat 80 "-")))
    (doseq [day (:days week)]
      (let [date (:date day)
            marker (if (= date today-str) ">" " ")
            planned (format-planned (:planned day))
            actual (if (:actual day) (format-actual (:actual day)) "")]
        (printf "%s%-8s  %-35s  %s%n" marker (format-date-short date) planned actual)))
    (println (str/join (repeat 80 "-")))
    (printf "%40s  Planned: %dkm  Actual: %.0fkm%n" "" (long plan-total) (double act-total))
    (println)))

(defn print-summary [weeks]
  (println)
  (println (str/join (repeat 72 "-")))
  (printf "%-12s  %-25s  %8s  %8s  %8s%n" "Week" "Block" "Target" "Planned" "Actual")
  (println (str/join (repeat 72 "-")))
  (let [today-monday (current-week-monday)
        truncate (fn [s n] (if (> (count s) n) (subs s 0 n) s))]
    (doseq [week weeks]
      (let [marker (if (= (:week-of week) today-monday) ">" " ")]
        (printf "%s%-11s  %-25s  %6dkm  %6dkm  %6.0fkm%n"
                marker
                (:week-of week)
                (truncate (or (:notes week) "") 25)
                (:target-km week)
                (long (planned-km week))
                (double (actual-km week))))))
  (println (str/join (repeat 72 "-")))
  (let [total-target (reduce + (map :target-km weeks))
        total-planned (reduce + (map planned-km weeks))
        total-actual (reduce + (map actual-km weeks))]
    (printf "%-14s  %-25s  %6dkm  %6dkm  %6.0fkm%n" "" "TOTAL" total-target (long total-planned) (double total-actual)))
  (println))

(defn find-current-week [weeks]
  (let [monday (current-week-monday)]
    (or (first (filter #(= (:week-of %) monday) weeks))
        (last (filter #(<= (compare (:week-of %) monday) 0) weeks))
        (first weeks))))

(defn run [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (when (and (seq args) (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
      (println "Usage: bb coaching:cal [OPTIONS]")
      (println)
      (println (cli/format-opts {:spec spec}))
      (System/exit 0)))
  (let [opts (cli/parse-opts args {:spec spec})
        weeks (load-calendar (:file opts))]
    (cond
      (:summary opts)
      (print-summary weeks)

      (:all opts)
      (doseq [w weeks] (print-week w))

      (:week opts)
      (if-let [w (first (filter #(= (:week-of %) (:week opts)) weeks))]
        (print-week w)
        (println "No week found for" (:week opts)))

      :else
      (print-week (find-current-week weeks)))))

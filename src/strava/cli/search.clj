(ns strava.cli.search
  (:require [babashka.cli :as cli]
            [clojure.string :as str]
            [strava.repo :as repo]
            [strava.tags :as tags]))

(def spec {:pattern {:alias :p :desc "Match part of the name"}
           :tag {:alias :t :coerce [] :desc "Filter by tag"}
           :no-tag {:alias :T :coerce [] :desc "Exclude activities with tag"}
           :dist-min {:coerce :int :desc "Min distance in km"}
           :dist-max {:coerce :int :desc "Max distance in km"}
           :hr-min {:coerce :int :desc "Min heartrate"}
           :hr-max {:coerce :int :desc "Max heartrate"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb search [OPTIONS]")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            coll (repo/find-activities opts)]
        (doseq [x coll]
          (println (format
                    "%s %s %5.1f [%s] [%s]"
                    (subs (:start_date x) 0 10)
                    (:id x)
                    (* 0.001 (:distance x))
                    (:name x)
                    (str/join " " (tags/all-tags (:id x) x)))))
        (println (format "%d record" (count coll))))))

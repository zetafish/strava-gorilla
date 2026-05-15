(ns strava.cli.search
  (:require [babashka.cli :as cli]
            [strava.repo :as repo]
            [strava.tags :as tags]))

(def spec {:pattern {:alias :p :coerce []}
           :tag {:alias :t :coerce :keyword :desc "Filter by tag"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb search -p <pattern> [--tag <tag>]")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            files (apply repo/find-by-pattern (:pattern opts))
            tag-filter (:tag opts)
            filtered (if tag-filter
                       (let [manual-tags (tags/load-tags)
                             all-activities (tags/load-all-activities)
                             activity-by-id (into {} (map (juxt :id identity)) all-activities)]
                         (filter (fn [f]
                                   (when-let [id (repo/extract-id f)]
                                     (let [activity (get activity-by-id id)
                                           all (if activity
                                                 (into (get manual-tags id #{}) (tags/auto-tags activity))
                                                 (get manual-tags id #{}))]
                                       (contains? all tag-filter))))
                                 files))
                       files)]
        (run! println filtered)
        (println (format "%d files" (count filtered))))))

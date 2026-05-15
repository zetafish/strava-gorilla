(ns strava.cli.search
  (:require [babashka.cli :as cli]
            [strava.repo :as repo]
            [strava.tags :as tags]))

(def spec {:pattern {:alias :p :coerce []}
           :tag {:alias :t :coerce :keyword :desc "Filter by tag"}
           :no-tag {:alias :T :coerce :keyword :desc "Exclude activities with tag"}
           :above {:coerce :double :desc "Min distance in km (exclusive)"}
           :below {:coerce :double :desc "Max distance in km (exclusive)"}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println "Usage: bb search -p <pattern> [--tag <tag>] [--no-tag <tag>] [--above KM] [--below KM]")
    (println)
    (println (cli/format-opts {:spec spec}))
    true))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})
            files (apply repo/find-by-pattern (:pattern opts))
            tag-filter (:tag opts)
            no-tag (:no-tag opts)
            above (:above opts)
            below (:below opts)
            needs-activities (or tag-filter no-tag above below)
            activity-by-id (when needs-activities
                             (into {} (map (juxt :id identity)) (tags/load-all-activities)))
            manual-tags (when (or tag-filter no-tag) (tags/load-tags))
            filtered (cond->> files
                       tag-filter
                       (filter (fn [f]
                                 (when-let [id (repo/extract-id f)]
                                   (let [activity (get activity-by-id id)
                                         all (if activity
                                               (into (get manual-tags id #{}) (tags/auto-tags activity))
                                               (get manual-tags id #{}))]
                                     (contains? all tag-filter)))))
                       no-tag
                       (remove (fn [f]
                                 (when-let [id (repo/extract-id f)]
                                   (let [activity (get activity-by-id id)
                                         all (if activity
                                               (into (get manual-tags id #{}) (tags/auto-tags activity))
                                               (get manual-tags id #{}))]
                                     (contains? all no-tag)))))
                       above
                       (filter (fn [f]
                                 (when-let [id (repo/extract-id f)]
                                   (when-let [activity (get activity-by-id id)]
                                     (> (/ (:distance activity 0) 1000.0) above)))))
                       below
                       (filter (fn [f]
                                 (when-let [id (repo/extract-id f)]
                                   (when-let [activity (get activity-by-id id)]
                                     (< (/ (:distance activity 0) 1000.0) below))))))]
        (run! println filtered)
        (println (format "%d files" (count filtered))))))

(ns strava.cli.search
  (:require [babashka.cli :as cli]
            [strava.repo :as repo]
            [strava.tags :as tags]))

(def spec {:pattern {:alias :p :coerce [] :require true}
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
            manual-tags (when tag-filter (tags/load-tags))
            filtered (if tag-filter
                       (filter (fn [f]
                                 (when-let [id (repo/extract-id f)]
                                   (let [activity (repo/find-activity id)
                                         all (if activity
                                               (into (get manual-tags id #{}) (tags/auto-tags activity))
                                               (get manual-tags id #{}))]
                                     (contains? all tag-filter))))
                               files)
                       files)]
        (run! println filtered))))

(ns strava.cli.tag
  (:require [strava.repo :as repo]
            [strava.tags :as tags]))

(defn parse-tag [s]
  (keyword (if (.startsWith s ":") (subs s 1) s)))

(defn format-activity [a]
  (format "%d  %s  %6.1fkm  %s"
          (:id a)
          (subs (:start_date_local a "") 0 10)
          (/ (:distance a 0) 1000.0)
          (:name a "")))

(defn run [args]
  (let [[cmd & rest-args] args]
    (case cmd
      "add"
      (let [[id-str & tag-strs] rest-args
            id (parse-long id-str)
            new-tags (set (map parse-tag tag-strs))]
        (tags/add-tags id new-tags)
        (println (format "Added %s to %d" new-tags id)))

      "rm"
      (let [[id-str & tag-strs] rest-args
            id (parse-long id-str)
            rm-tags (set (map parse-tag tag-strs))]
        (tags/remove-tags id rm-tags)
        (println (format "Removed %s from %d" rm-tags id)))

      "show"
      (let [id (parse-long (first rest-args))
            activity (repo/find-activity id)
            all (if activity
                  (tags/all-tags id activity)
                  (get (tags/load-tags) id #{}))]
        (println (format "%d: %s" id (pr-str all))))

      "list"
      (let [tag (parse-tag (first rest-args))
            activities (tags/find-by-tag tag)]
        (if (seq activities)
          (doseq [a (sort-by :start_date_local activities)]
            (println (format "  %s  [%s]"
                             (format-activity a)
                             (pr-str (tags/all-tags (:id a) a)))))
          (println "No activities with tag" tag)))

      "review"
      (let [candidates (tags/needs-review)]
        (if (seq candidates)
          (do
            (println (format "%d activities >42km without :race tag:" (count candidates)))
            (println)
            (doseq [a (sort-by :start_date_local candidates)]
              (println (str "  " (format-activity a)))))
          (println "All >42km activities are tagged.")))

      (do
        (println "Usage: bb tag <command> [args]")
        (println)
        (println "Commands:")
        (println "  add <id> <tag1> <tag2> ...   Add tags to activity")
        (println "  rm  <id> <tag1> <tag2> ...   Remove tags from activity")
        (println "  show <id>                    Show all tags (manual + auto)")
        (println "  list <tag>                   List activities with tag")
        (println "  review                       Show untagged race candidates")))))

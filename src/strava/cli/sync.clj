(ns strava.cli.sync
  (:require [babashka.cli :as cli]
            [strava.repo :as repo]))

(def spec {:year {:alias :y :require true :coerce :int}
           :month {:alias :m :coerce :int}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println (cli/format-opts {:spec spec}))
    true))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})]
        (if (:month opts)
          (repo/sync-month (:year opts) (:month opts))
          (repo/sync-year (:year opts))))))

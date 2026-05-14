(ns strava.cli.search
  (:require [babashka.cli :as cli]
            [strava.repo :as repo]))

(def spec {:pattern {:alias :p :coerce [] :require true}})

(defn help-requested [args]
  (when (or (not (seq args))
            (:help (cli/parse-opts args {:spec {:help {:alias :h}}})))
    (println (cli/format-opts {:spec spec}))
    true))

(defn run [args]
  (or (help-requested args)
      (let [opts (cli/parse-opts args {:spec spec})]
        (println opts)
        (run! println (apply repo/find-by-pattern (:pattern opts))))))

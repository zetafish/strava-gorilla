(ns strava.cli
  (:require [babashka.cli :as cli]
            [strava.cli.eff :as eff]
            [strava.cli.search :as search]
            [strava.cli.sync :as sync]
            [strava.repo :as repo]))

(defn eff [args]
  (eff/run args))

(defn sync [args]
  (sync/run args))

(defn search [args]
  (search/run args))

(defn fetch-desc [args]
  (let [opts (cli/parse-opts args {:spec {:id {:coerce :long :require true}}})]
    (repo/get-description-by-activity-id (:id opts))
    (println "Saved description for activity" (:id opts))))

(ns strava.cli
  (:require [babashka.cli :as cli]
            [strava.api :as api]
            [strava.cli.eff :as eff]
            [strava.cli.heatmap :as heatmap]
            [strava.cli.histogram :as histogram]
            [strava.cli.line :as line]
            [strava.cli.scatter :as scatter]
            [strava.cli.search :as search]
            [strava.cli.sync :as sync]
            [strava.cli.tag :as tag]
            [strava.cli.trend :as trend]
            [strava.repo :as repo]))

(defn eff [args]
  (eff/run args))

(defn sync [args]
  (sync/run args))

(defn search [args]
  (search/run args))

(defn scatter [args]
  (scatter/run args))

(defn trend [args]
  (trend/run args))

(defn histogram [args]
  (histogram/run args))

(defn line [args]
  (line/run args))

(defn heatmap [args]
  (heatmap/run args))

(defn tag [args]
  (tag/run args))

(defn fetch-desc [args]
  (let [opts (cli/parse-opts args {:spec {:id {:coerce :long :require true}}})]
    (repo/get-description-by-activity-id (:id opts))
    (println "Saved description for activity" (:id opts))))

(defn details [args]
  (let [opts (cli/parse-opts args {:spec {:id {:coerce :long :require true}}})]
    (println (repo/get-description-by-activity-id (:id opts)))))

(defn refresh-token [_args]
  (api/refresh-token!)
  (println "Token refreshed"))

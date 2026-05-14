(ns strava.cli
  (:require [strava.cli.eff :as eff]
            [strava.cli.search :as search]
            [strava.cli.sync :as sync]))

(defn eff [args]
  (eff/run args))

(defn sync [args]
  (sync/run args))

(defn search [args]
  (search/run args))

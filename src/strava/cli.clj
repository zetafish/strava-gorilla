(ns strava.cli
  (:require [strava.cli.eff :as eff]))

(defn eff [args]
  (eff/run args))

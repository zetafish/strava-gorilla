(ns strava.cli
  (:require [babashka.cli :as cli]
            [strava.api :as api]
            [strava.cli.compare :as compare]
            [strava.cli.eff :as eff]
            [strava.cli.heatmap :as heatmap]
            [strava.cli.histogram :as histogram]
            [strava.cli.line :as line]
            [strava.cli.load :as load-cmd]
            [strava.cli.scatter :as scatter]
            [strava.cli.search :as search]
            [strava.cli.similar :as similar]
            [strava.cli.stats :as stats]
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

(defn load [args]
  (load-cmd/run args))

(defn similar [args]
  (similar/run args))

(defn compare-runs [args]
  (compare/run args))

(defn stats [args]
  (stats/run args))

(defn refresh-token [_args]
  (api/refresh-token!)
  (println "Token refreshed"))

(def table
  [;; {:cmds ["plot"] :fn print-plot-help}
   {:cmds ["plot" "histogram"] :fn #(histogram/run (:opts %))}
   {:cmds ["plot" "scatter"] :fn #(scatter/run (:opts %))}

;; {:cmds ["plot" "line"]} :fn
   ])

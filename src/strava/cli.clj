(ns strava.cli
  (:require ; [babashka.cli :as cli]
   [strava.api :as api]))

;; (defn eff [args]
;;   (eff/run args))

;; (defn sync [args]
;;   (sync/run args))

;; (defn search [args]
;;   (search/run args))

;; (defn scatter [args]
;;   (scatter/run args))

;; (defn histogram [args]
;;   (histogram/run args))

;; (defn line [args]
;;   (line/run args))

;; (defn heatmap [args]
;;   (heatmap/run args))

;; (defn tag [args]
;;   (tag/run args))

;; (defn fetch-desc [args]
;;   (let [opts (cli/parse-opts args {:spec {:id {:coerce :long :require true}}})]
;;     (repo/get-description-by-activity-id (:id opts))
;;     (println "Saved description for activity" (:id opts))))

;; (defn details [args]
;;   (let [opts (cli/parse-opts args {:spec {:id {:coerce :long :require true}}})]
;;     (println (repo/get-description-by-activity-id (:id opts)))))

;; (defn load [args]
;;   (load-cmd/run args))

;; (defn stats [args]
;;   (stats/run args))

;; (defn calendar [args]
;;   (calendar/run args))

;; (defn monthly [args]
;;   (monthly/run args))

;; (defn weekly [args]
;;   (monthly/run-weekly args))

(defn refresh-token [_args]
  (api/refresh-token!)
  (println "Token refreshed"))

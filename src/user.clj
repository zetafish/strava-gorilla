(ns user
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [strava.api :as api]
            [strava.cli.sync :as sync]
            [strava.parser.gpx :as gpx]
            [strava.parser.tcx :as tcx]
            [strava.repo :as repo]
            [strava.track :as track]))

;; (doseq [y [;; 2026
;;            2025
;;            2024
;;            2023
;;            2022
;;            2021
;;            2020
;;            2019
;;            2018
;;            2017
;;            2016
;;            2015
;;            2014]]
;;   (sync/sync-year y))

;; (api/refresh-token!)

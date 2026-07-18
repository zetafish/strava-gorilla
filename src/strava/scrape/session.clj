(ns strava.scrape.session
  (:require [babashka.http-client :as http]
            [clojure.edn :as edn]))

(def auth-file ".auth.edn")

(defn session-cookie []
  (or (:session-cookie (edn/read-string (slurp auth-file)))
      (throw (ex-info "Missing :session-cookie in .auth.edn" {}))))

(defn cookie-header []
  (str "_strava4_session=" (session-cookie)))

(defn GET [url & {:keys [as] :or {as :string}}]
  (let [resp (http/request {:url url
                            :method :get
                            :headers {"cookie" (cookie-header)
                                      "user-agent" "Mozilla/5.0 (strava-scrape)"}
                            :as as
                            :throw false})]
    (when-not (= 200 (:status resp))
      (throw (ex-info "Non-200 from Strava"
                      {:status (:status resp) :url url})))
    (:body resp)))

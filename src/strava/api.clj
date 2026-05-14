(ns strava.api
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint]
            [clojure.string :as str])
  (:import (org.jsoup
            Jsoup)))

;; Overall Rate Limits 200 requests every 15 minutes, 2,000 daily
;; Read Rate Limits 100 requests every 15 minutes, 1,000 daily

(def pp clojure.pprint/pprint)

(def max-per-page 200)

(def auth (edn/read-string (slurp ".auth.edn")))

(def stream-keys ["time"
                  "distance"
                  "latlng"
                  "altitude"
                  "velocity_smooth"
                  "heartrate"
                  "cadence"
                  "power"
                  "moving"
                  "grade_smooth"])

(defn read-creds []
  (fs/touch ".creds.edn")
  (edn/read-string (slurp ".creds.edn")))

(defonce creds (atom (read-creds)))

(add-watch creds :store
           (fn [_key _ref _old-value new-value]
             (spit ".creds.edn"
                   (with-out-str
                     (clojure.pprint/pprint new-value)))))

(def start-auth "https://www.strava.com/oauth/authorize?client_id=4863&response_type=code&redirect_uri=http://localhost/exchange_token&approval_prompt=force&scope=read,activity:read_all")

(defn exchange-code! [code]
  (let [new-creds (-> (http/request {:url "https://www.strava.com/oauth/token"
                                     :method :post
                                     :form-params {"client_id" (:client-id auth)
                                                   "client_secret" (:client-secret auth)
                                                   "code" code
                                                   "grant_type" "authorization_code"}})
                      :body
                      (json/decode true))]
    (swap! creds assoc
           :access-token (:access_token new-creds)
           :refresh-token (:refresh_token new-creds))))

(defn refresh-token! []
  (let [data (-> (http/request {:method :post
                                :url "https://www.strava.com/oauth/token"
                                :form-params {:client_id (:client-id auth)
                                              :client_secret (:client-secret auth)
                                              :grant_type "refresh_token"
                                              :refresh_token (:refresh-token @creds)}})
                 :body
                 (json/decode true))]
    (swap! creds merge data)))

(defn iso->epoch [iso]
  (.getEpochSecond (java.time.Instant/parse iso)))

(defn prettier [json-str]
  (-> json-str
      json/decode
      (json/encode {:pretty true})))

(defn build [request]
  (-> request
      (assoc :url (str "https://www.strava.com/api/v3" (:path request)))
      (assoc-in [:headers "Authorization"] (str "Bearer " (:access-token @creds)))
      (assoc :throw false)))

(defn call [request]
  (-> request
      build
      http/request
      ;; ((fn [r] (println r) r))
      :body
      (json/decode true)))

(defn get-athlete []
  (call {:path "/athlete"}))

(defn get-activity [activity-id]
  (call {:path (str "/activities/" activity-id)}))

(defn list-activities [& {:keys [before after page per-page]}]
  (call {:path "/athlete/activities"
         :query-params (cond-> {}
                         after (assoc :after (iso->epoch after))
                         before (assoc :before (iso->epoch before))
                         page (assoc :page page)
                         per-page (assoc :per_page per-page))}))

(defn get-activity-streams [activity-id & {:keys [keys]}]
  (call {:path (str "/activities/" activity-id "/streams")
         :query-params {:keys (str/join "," keys)
                        :key_by_type true}}))

(defn download-original
  "No rate limit, uses session cookie"
  [activity-id file]
  (let [data (-> {:url (format "https://www.strava.com/activities/%s/export_original" activity-id)
                  :headers {"cookie" (str "_strava4_session=" (:session-cookie auth))}
                  :as :bytes
                  :throw false}
                 http/request
                 :body)]
    (io/copy data (io/file file))))

(defn fetch-description
  "No rate limit, uses session cookie"
  [activity-id]
  (let [html (-> {:url (format "https://www.strava.com/activities/%s" activity-id)
                  :headers {"cookie" (str "_strava4_session=" (:session-cookie auth))}
                  :as :string
                  :throw false}
                 http/request
                 :body)
        doc (Jsoup/parse html)
        text-nodes (.selectXpath doc "//div[@class='content']/p/text()", org.jsoup.nodes.TextNode)]
    (str/join "\n\n" (map #(.text %) text-nodes))))

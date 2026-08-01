(ns strava.cache
  "Simple per-key JSON file cache under .data/<bucket>/<key>.json.

   No invalidation: same key => same value. Delete the file to bust."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]))

(def ^:private root ".data")

(defn- cache-file [bucket key]
  (fs/file root (name bucket) (str key ".json")))

(defn has? [bucket key]
  (fs/exists? (cache-file bucket key)))

(defn read-cached [bucket key]
  (json/decode (slurp (str (cache-file bucket key))) true))

(defn write-cache! [bucket key value]
  (let [f (cache-file bucket key)]
    (fs/create-dirs (fs/parent f))
    (spit (str f) (json/encode value {:pretty true}))
    value))

(defn evict! [bucket key]
  (fs/delete-if-exists (cache-file bucket key)))

(defn through-cache
  "Return the cached value at bucket/key. On miss, call (value-fn),
   write the result, and return the result"
  [bucket key value-fn]
  (when-not (has? bucket key)
    (write-cache! bucket key (value-fn)))
  (read-cached bucket key))

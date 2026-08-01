(ns strava.cli.pmap-vt
  (:import (java.util.concurrent
            Callable
            Executors)))

(defonce executor (Executors/newVirtualThreadPerTaskExecutor))

(defn pmap-vt
  [f coll]
  ;; 1. Submit all jobs to the virtual thread executor as Callable tasks
  (let [tasks (mapv (fn [item]
                      (.submit executor ^Callable (fn [] (f item))))
                    coll)]
    ;; 2. Read the results lazily by mapping over the returned future objects
    (mapv (fn [fut] (.get fut)) tasks)))

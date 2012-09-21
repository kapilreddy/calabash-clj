(ns calabash-clj.platforms.util
  (:require [calabash-jvm.http :as http]))

(defn retry
  [fn & {:keys [retries max-retries]
         :or {retries 0 max-retries 10}}]
  (try
    (println (fn))
    (catch Exception e
      (when (= retries max-retries)
        true)
      (Thread/sleep 200)
      (retry fn :retries (inc retries)))))

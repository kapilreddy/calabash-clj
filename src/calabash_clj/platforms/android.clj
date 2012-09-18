(ns calabash-clj.platforms.android
  (:require [calabash-jvm.http :as http]))

(defn command
  [command & args]
  (http/req {:method :post
              :path "/"
              :as :json}
             {:command command
              :arguments args}))

(defn touch
  [type selector]
  (command "touch" type selector))


(defn enter-text
  [text type selector]
  (command "set_text" type selector text))


(defn query
  [type selector]
  (command "query" type selector))

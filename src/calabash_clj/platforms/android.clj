(ns calabash-clj.platforms.android
  (:require [calabash-jvm.http :as http])
  (:use [calabash-clj.util :only [run-sh]]))

(def ^:dynamic *device-name* nil)


(defn run-on-device
  [fn ip server-port device-name]
  (println ip server-port)
  (binding [calabash-jvm.env/*endpoint* (format "http://%s:%s" ip server-port)
            *device-name* device-name]
    (fn)))


(defn run-on-devices
  [fn devices]
  (doseq [{:keys [ip server-port name]} devices]
    (run-on-device fn ip server-port name)))


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
  ([text type selector]
     (command "set_text" type selector text))
  ([text]
     (run-sh (format "adb -s %s shell input text '%s'" *device-name* text))))


(defn query
  [type selector]
  (command "query" type selector))


(defn press-key
  [key]
  (run-sh (format "adb -s %s shell input keyevent %s" *device-name* key)))

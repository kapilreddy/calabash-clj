(ns calabash-clj.platforms.android
  (:require [calabash-jvm.http :as http])
  (:use [calabash-clj.util :only [run-sh]]))

(def ^:dynamic *device-port* nil)


(defn run-on-device
  [fn server-port device-port]
  (binding [calabash-jvm.env/*endpoint* (format "http://localhost:%s" server-port)
            *device-port* device-port]
    (fn)))


(defn run-on-devices
  [fn devices]
  (doseq [{:keys [server-port port]} devices]
    (future (run-on-device fn server-port port))))


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
     (run-sh (format "adb -s emulator-%s shell input text %s" *device-port* text))))


(defn query
  [type selector]
  (command "query" type selector))


(defn press-key
  [key]
  (run-sh (format "adb -s emulator-%s shell input keyevent %s" *device-port* key)))

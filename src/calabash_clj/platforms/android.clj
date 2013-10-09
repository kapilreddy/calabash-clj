(ns calabash-clj.platforms.android
  (:require [calabash-jvm.http :as http]
            [clj-http.client :as client]
            [calabash-jvm.core :as cjc]
            [clojure.java.io :as io])
  (:use [calabash-clj.util :only [run-sh]]
        [calabash-clj.platforms.util :only [retry]]
        [calabash-clj.util :only [gen-gif]]))

(def ^:dynamic *device-name* nil)


(defn run-on-device
  [fn ip server-port device-name]
  (binding [calabash-jvm.env/*endpoint* (format "http://%s:%s" ip server-port)
            *device-name* device-name]
    (fn)))


(defn run-on-devices
  [test-fn devices]
  (map (fn [{:keys [ip server-port name]}]
         (println ip server-port name)
         (run-on-device test-fn ip server-port name))
       devices))


(defn query
  [query & args]
  (http/map-views query "query"))


(defn command
  [command & args]
  (http/req {:method :post
             :path "/"
             :as :json}
            {:command command
             :arguments args}))


(defn ready*
  []
  (http/req {:method :post
             :path "/ready"
             :as :text}
            {}))


(defn ready
  []
  (retry ready*))


(defn screenshot*
  [filename]
  (run-sh (format "adb -s %s shell /system/bin/screencap -p /sdcard/screenshot.png" *device-name*)
          (format "adb -s %s pull /sdcard/screenshot.png %s" *device-name* filename)))


(defn regex-file-seq
  "Lazily filter a directory based on a regex."
  [re dir]
  (filter #(re-find re (.getPath %)) (file-seq (io/file dir))))


(defn delete-all-files
  [re dir]
  (doseq [f (regex-file-seq re dir)]
    (io/delete-file f)))


(def screenshot-num (atom 0))


(defn reset-screenshot-num
  []
  (reset! screenshot-num 0))


(defn screenshot
  [prefix]
  (screenshot* (format "%s-%05d.png"
                       prefix
                       @screenshot-num))
  (swap! screenshot-num inc))


(defn generate-gif-&-cleanup
  [prefix]
  (gen-gif prefix)
  (delete-all-files #".*png" ".")
  (reset-screenshot-num))


(defn touch
  [type selector]
  (command "touch" type selector))


(defn press-key
  [key]
  (run-sh (format "adb -s %s shell input keyevent %s" *device-name* key)))


(defn enter-text
  ([text type selector]
     (command "set_text" type selector text))
  ([text]
     (let [[s & coll] (clojure.string/split text #"\s")]
       (run-sh (format "adb -s %s shell input text \"%s\" " *device-name* s))
       (doseq [t coll]
         (press-key 62)
         (run-sh (format "adb -s %s shell input text \"%s\" " *device-name* t))))))


(defn click
  [id]
  (command "click_on_view_by_id"
           id))


(defn wait
  [id]
  (command "wait_for_view_by_id"
           id))

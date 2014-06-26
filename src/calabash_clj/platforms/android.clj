(ns calabash-clj.platforms.android
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [calabash-clj.util :refer [run-sh]]
            [calabash-clj.platforms.util :refer [retry]]
            [clojure.data.json :refer [write-str]]
            [slingshot.slingshot :refer [throw+]]
            [clojure.tools.logging :refer [info error]]))

(def ^:dynamic *conn-timeout* 5000)
(def ^:dynamic *socket-timeout* 5000)

(defn ^:dynamic *retry-handler*
  [ex try-count http-context]
  (<= try-count 2))

(def default-spec {:content-type :json
                   :socket-timeout *socket-timeout*
                   :check-json? true
                   :conn-timeout *conn-timeout*
                   :retry-handler *retry-handler*})

(def ^:dynamic *device-name* nil)
(def ^:dynamic *device-num* nil)
(def ^:dynamic *endpoint* nil)


;; Calabash-clj related commands
(defn run-on-device
  [test-fn ip server-port device-name & [i]]
  (let [let [i (or i 0)]]
    (binding [*endpoint* (format "http://%s:%s" ip server-port)
              *device-name* device-name
              *device-num* i]
      (test-fn))))


(defn run-on-devices
  [test-fn devices]
  (mapv (fn [{:keys [ip server-port name]} i]
          (run-on-device test-fn ip server-port name i))
        devices
        (range)))


(defn calabash-post
  [path spec body]
  (client/post (format "%s%s" *endpoint* path)
               (merge default-spec spec {:body (write-str body)})))


;; Commands to run on android device through http.
(defn command
  [command & args]
  (calabash-post "/"
                 {:as :json}
                 {:command command
                  :arguments args}))


(defn op-map [op & args]
  {:method_name op
   :arguments (or args [])})


(defn map-views
  "Reaches the map endpoint via POST.
   Takes a UIQuery, and op name and optional args"
  [q op & args]
  (get-in (calabash-post "/map"
                 {:as :json}
                 {:query q
                  :operation (apply op-map op args)})
          [:body :results]))


(defn query
  [q]
  (map-views q "query"))


(defn touch
  [q]
  (let [[{:keys [rect]} & _] (query q)
        {:keys [center_x center_y]} rect]
    (command "touch_coordinate" center_x center_y)))


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


(defn delete-text
  ([]
     (press-key 67))
  ([text-length]
     (doseq [n (range 0 text-length)]
       (press-key 67))))


(defn count-connected-devices
  []
  ;; Decrement count b/c output contains string
  ;; "List of devices attached"
  (dec (count (re-seq #"device|offline"
                      (:out (run-sh "adb devices"))))))


(defn restart-adb-server
  []
  (loop [retry-count 0 device-count (count-connected-devices)]
    (if (= retry-count 5)
      (do
        (error "Unable to start adb server")
        (throw+ {:type :adb-server/timeout}))
      (do
        (run-sh "adb kill-server && adb start-server")
        (when-not (= device-count (count-connected-devices))
          (info "Unable to connect adb server. Retrying connect")
          (recur (inc retry-count) device-count))))))


(defn start-logcat-server
  "Starts fresh adb logcat server, kills if already running

   For more information about adb logcat:
http://developer.android.com/tools/debugging/debugging-log.html"
  [{:keys [log-format file-name app-name tag app-priority tag-priority all-tag-priority]}]
  (restart-adb-server)
  ;; remove remote file if already exists
  (run-sh (format "adb shell rm %s" file-name))
  ;; clear logcat buffer
  (run-sh "adb logcat -c")
  (run-sh (format "adb logcat -v %s -f %s %s:%s %s:%s *:%s"
                  log-format
                  file-name
                  app-name
                  app-priority
                  tag
                  tag-priority
                  all-tag-priority)))


(defn download-remote-file
  [remote-file-name local-file-name]
  (run-sh (format "adb pull %s %s" remote-file-name local-file-name)))


(defn click
  [id]
  (command "click_on_view_by_id"
           id))


(defn wait
  [id]
  (command "wait_for_view_by_id"
           id))


(defn ready*
  []
  (client/post "/ready"
               {:as :text}))


(defn ready
  []
  (retry ready*))


(defn screenshot*
  [filename]
  (run-sh (format "adb -s %s shell /system/bin/screencap -p /sdcard/screenshot.png" *device-name*)
          (format "adb -s %s pull /sdcard/screenshot.png %s" *device-name* filename)))

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

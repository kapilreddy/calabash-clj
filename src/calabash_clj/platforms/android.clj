(ns calabash-clj.platforms.android
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [calabash-clj.util :refer [run-sh]]
            [calabash-clj.platforms.util :refer [retry]]
            [clojure.data.json :refer [write-str]]))

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
(def ^:dynamic *endpoint* nil)


;; Calabash-clj related commands
(defn run-on-device
  [fn ip server-port device-name]
  (binding [*endpoint* (format "http://%s:%s" ip server-port)
            *device-name* device-name]
    (fn)))


(defn run-on-devices
  [test-fn devices]
  (map (fn [{:keys [ip server-port name]}]
         (run-on-device test-fn ip server-port name))
       devices))


(defn calabash-post
  [path spec body]
  (client/post (format "%s%s" *endpoint* path)
               (merge default-spec spec {:body (write-str body)})))


;; Commands to run on android device through http.
(defn command
  [command & args]
  (println (calabash-post "/"
                 {:as :json}
                 {:command command
                  :arguments args})))


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

(ns calabash-clj.build.android
  (:require [calabash-clj.platforms.util :as util])
  (:use [clojure.tools.logging :only [info error]]
        [calabash-clj.util]))

(def android-server-port 7102)

(def server-apk-path "/test-server/bin/Test.apk")

(def server-tar "test_server.tar.gz")


(defn get-apk-info
  [apk-path]
  (let [apk-info-str (:out (run-sh (str "aapt dump badging " apk-path)))]
    {:package-name (second (re-find #"package: name='([0-9a-zA-Z\.]*)'"
                                    apk-info-str))
     :main-activity (second (re-find #"launchable-activity: name='([0-9a-zA-Z\.]*)'"
                                     apk-info-str))}))


(defn build-output->apk-name
  [build-output]
  (second (re-find #"Debug Package: (.*)\n"
                   build-output)))


(defn build-project
  [project-path]
  (let [build-output (run-with-dir
                       project-path
                       "android update project -p . --target 13"
                       "ant clean"
                       "ant -e debug")]
    (if (seq (:err build-output))
      (throw (Exception. (:err build-output)))
      (let [apk-path (build-output->apk-name (:out build-output))
            {:keys [package-name main-activity]} (get-apk-info apk-path)
            test-build-command (format "ant -e package -Dtested.package_name=\"%s\" -Dtested.main_activity=\"%s\" -Dtested.project.apk=\"%s\" -Dandroid.api.level=\"13\" -Dkey.store=\"$HOME/.android/debug.keystore\" -Dkey.store.password=\"android\" -Dkey.alias=\"androiddebugkey\" -Dkey.alias.password=\"android\" "
                                       package-name main-activity apk-path)
            test-build-op (run-with-dir
                            (str project-path "test-server")
                            "ant clean"
                            test-build-command)]
        (if (seq (:err test-build-op))
          (throw (Exception. (:err test-build-op)))
          apk-path)))))


(defn install-apk
  [apk-path device-name]
  (run-sh (format "adb -s %s install -r %s" device-name apk-path)))


(defn forward-port
  [device-name local remote]
  (run-sh (format"adb -s %s forward tcp:%s tcp:%s" device-name local remote)))


(defn unlock-device
  [device-name]
  (run-sh (format "adb -s %s shell input keyevent 82" device-name))
  (run-sh (format "adb -s %s shell input keyevent 4" device-name)))


(defn instrument-device
  [package-name device-name]
  (future (run-sh (format "adb -s %s shell am instrument -w -e class sh.calaba.instrumentationbackend.InstrumentationBackend %s.test/sh.calaba.instrumentationbackend.CalabashInstrumentationTestRunner" device-name package-name))))


(defn start-emulators
  [emulators]
  (doseq [avd emulators]
    (future (run-sh (format "emulator -avd %s" avd)))))


(defn list-emulators
  []
  (:out (run-sh "adb devices")))


(defn get-device-lines
  [device-list-str]
  (let [[first & devices] (clojure.string/split device-list-str #"\n")]
       devices))


(defn parse-devices-list
  [device-list-str]
  (map (fn [str]
         (let [[name status] (clojure.string/split str #"\t")]
           {:port (Integer/parseInt (re-find #"\d{4}" name))
            :name name
            :status status}))
       (get-device-lines device-list-str)))



(defn copy-test-server
  [project-path]
  (let [server-tar-loc (.getCanonicalPath (clojure.java.io/as-file (clojure.java.io/resource server-tar)))]
    (run-sh (format "tar zxf %s -C %s" server-tar-loc project-path))))

(defn restart-adb
  []
  (run-sh
   "adb kill-server"
   "adb start-server"))


(defn -main
  "Build a project and run tests on list of emulators"
  [project-path emulators calabash-tests]
  (info (format "Copying test-server to %s" project-path))
  (copy-test-server project-path)
  (info "Building project")
  (let [apk-path (build-project project-path)]
    (info "Restarting adb")
    (restart-adb)
    (info "Starting emulators")
    (start-emulators emulators)
    (loop [devices (get-device-lines (list-emulators))]
      (when-not (and (= (count devices)
                        (count emulators))
                     (zero? (count (filter #(= "offline"
                                               (:status %1))
                                           (parse-devices-list (list-emulators))))))
        (do (Thread/sleep 2000)
            (recur (get-device-lines (list-emulators))))))
    (Thread/sleep 2000)
    (info "Installing app on devices")
    (let [devices (map (fn [device n]
                         (assoc device :server-port n))
                       (parse-devices-list (list-emulators))
                       (range 5010 5100))]
      (doseq [{:keys [name]} devices]
        (install-apk apk-path name)
        (install-apk (str project-path server-apk-path) name))
      (Thread/sleep 2000)
      (info "Port forwarding for devices")
      (doseq [{:keys [name server-port]} devices]
        (forward-port name server-port android-server-port))
      (Thread/sleep 2000)
      (info "Unlocking screens")
      (doseq [{:keys [name]} devices]
        (unlock-device name))
      (Thread/sleep 2000)
      (info "Starting Calabash servers")
      (let [{:keys [package-name]} (get-apk-info apk-path)]
        (doseq [{:keys [name]} devices]
          (instrument-device package-name name)))
      (Thread/sleep 10000)
      (info "Running calabash tests")
      (doseq [{:keys [server-port]} devices]
        (future (util/run-on-device calabash-tests
                                    server-port))))))

(ns calabash-clj.build.android
  (:require [calabash-clj.platforms.android :as android])
  (:use [clojure.tools.logging :only [info error]]
        [clojure.java.io :only [resource copy file]]
        [calabash-clj.util :only [run-sh run-with-dir some-truthy]]))

(def android-server-port 7102)

(def server-apk-path "/test-server/bin/Test.apk")

(def server-tar "test-server.tar.gz")

(def android-target-version 19)


(defn get-parent-directory
  "Returns parent directory of given file path"
  [file-path]
  (let [command-output (:out (run-sh (str "dirname " file-path)))]
    (str (second (re-find #"(.*)\n"
                     command-output))
         "/")))


(defn get-apk-info
  [apk-path]
  (let [apk-info-str (:out (run-sh (str "aapt dump badging " apk-path)))]
    {:package-name (second (re-find #"package: name='([0-9a-zA-Z\.]*)'"
                                    apk-info-str))
     :main-activity (second (re-find #"launchable-activity: name='([0-9a-zA-Z\.]*)'"
                                     apk-info-str))
     :target-version (second (re-find #"targetSdkVersion:'([0-9a-zA-Z\.]*)'"
                                     apk-info-str))}))


(defn build-output->apk-name
  [build-output]
  (second (re-find #"Debug Package: (.*)\n"
                   build-output)))


(defn build-android-project
  "Builds android project and returns apk path"
  [project-path]
  (let [build-output (run-with-dir project-path
                                   (format "android update project -p . --target %s" android-target-version)
                                   "ant clean"
                                   "ant -e debug")]
    (if (seq (:err build-output))
      (throw (Exception. (:err build-output)))
      (build-output->apk-name (:out build-output)))))


(defn copy-test-server
  [project-path]
  (let [tmp-loc (format "/tmp/%s" server-tar)]
    (copy (file (resource server-tar)) (file tmp-loc))
    (run-sh (format "rm -rf %s/test-server" project-path)
            (format "tar zxf %s -C %s" tmp-loc project-path))))


(defn build-test-server
  "Build calabash test server."
  [apk-path]
  (let [server-directory-path (get-parent-directory apk-path)]
    (info (format "Copying test-server to %s" server-directory-path))
    (copy-test-server server-directory-path)
    (let [{:keys [package-name main-activity]} (get-apk-info apk-path)
          test-build-command (format "ant -e package -Dtested.package_name=\"%s\" -Dtested.main_activity=\"%s\" -Dtested.project.apk=\"%s\" -Dandroid.api.level=\"%s\" -Dkey.store=\"$HOME/.android/debug.keystore\" -Dkey.store.password=\"android\" -Dkey.alias=\"androiddebugkey\" -Dkey.alias.password=\"android\" "
                                     package-name main-activity apk-path android-target-version)
          test-build-op (run-with-dir
                         (str server-directory-path "test-server")
                         "ant clean"
                         test-build-command)]
      (when (seq (:err test-build-op))
        (throw (Exception. (:err test-build-op)))))))


(defn install-apk
  [apk-path device-name]
  (run-sh (format "adb -s %s install -r %s" device-name apk-path)))


(defn uninstall-app
  [apk-path device-name]
  (let [{:keys [package-name]} (get-apk-info apk-path)]
    (run-sh (format "adb -s %s shell pm uninstall -k %s" device-name package-name))))


(defn forward-port
  [device-name local remote]
  (run-sh (format"adb -s %s forward tcp:%s tcp:%s" device-name local remote)))


(defn unlock-device
  [package-name device-name]
  (run-sh (format "adb -s %s shell am start -a android.intent.action.MAIN -n %s.test/sh.calaba.instrumentationbackend.WakeUp" device-name package-name)))


(defn instrument-device
  [apk-path device-name]
  (let [{:keys [package-name main-activity]} (get-apk-info apk-path)]
    (future (run-sh (format "adb -s %s shell am instrument -w -e target_package %s -e main_activity %s -e test_server_port %s -e class sh.calaba.instrumentationbackend.InstrumentationBackend %s.test/sh.calaba.instrumentationbackend.CalabashInstrumentationTestRunner" device-name package-name main-activity android-server-port  package-name)))))


(defn start-emulators
  [emulators]
  (doseq [avd emulators]
    (future (run-sh (format "emulator -avd %s" avd)))))


(defn list-devices
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
           {:name name
            :status status}))
       (get-device-lines device-list-str)))


(defn restart-adb
  []
  (run-sh
   "adb kill-server"
   "adb start-server"))


(defn get-device-ip*
  [name interface]
  (let [ip-out (:out (run-sh
                      (format "adb -s %s shell ifconfig %s" name interface)))]
    (second (re-find #"ip\s([\d\.]*)\s" ip-out))))


(defn get-device-ip
  [name]
  (or (get-device-ip* name "eth0")
      (get-device-ip* name "wlan0")))


(defn is-emulator?
  [name]
  (re-find #"^emulator" name))


(defn wait-for-emulators
  [emulators]
  (loop [devices (get-device-lines (list-devices))]
    (when-not (and (>= (count devices)
                       (count emulators))
                   (zero? (count (filter #(= "offline"
                                             (:status %1))
                                         (parse-devices-list (list-devices))))))
      (do (Thread/sleep 2000)
          (recur (get-device-lines (list-devices)))))))


(defn run-on-connected-devices
  "Build a project and run tests on list of emulators

   opts: {:project-path - path to android project.
          :apk-path - path to apk file}
   calabash-tests: calabash queries

   For Ex.:
   (run-on-connected-devices {:apk-path \"path/to/apk-file\"}
                             (fn []
                               ;; Write calabash queries
                               (android/click \"button1\"))

  OR
  (run-on-connected-devices {:project-path \"path/to/android-project/\"
                             (fn []
                               ;; Write calabash queries
                               (android/click \"button1\"))"
  [{:keys [project-path apk-path]
    :as opts} calabash-tests]
  {:pre [(some-truthy opts :project-path :apk-path)]}
  (let [{:keys [project-path apk-path]} opts
        apk-path (if apk-path
                   apk-path
                   (build-android-project project-path))
        {:keys [package-name]} (get-apk-info apk-path)]
    (info "Building project")
    (build-test-server apk-path)
    (println apk-path)
    (info "Installing app on devices")
    (let [devices (map (fn [{:keys [name] :as device} n]
                         (let [emulator? (is-emulator? name)]
                           (assoc device :server-port (if emulator?
                                                        n
                                                        android-server-port)
                                  :emulator? emulator?
                                  :ip (if emulator?
                                        "localhost"
                                        (get-device-ip name)))))
                       (parse-devices-list (list-devices))
                       (range 5010 5100))]
      (doseq [{:keys [name]} devices]
        (uninstall-app apk-path name)
        (install-apk apk-path name)
        (install-apk (str (get-parent-directory apk-path)
                          server-apk-path)
                     name))
      (Thread/sleep 2000)
      (info "Port forwarding for devices")
      (doseq [{:keys [name server-port]} devices]
        (forward-port name server-port android-server-port))
      (Thread/sleep 2000)
      (info "Starting Calabash servers")
      (let [reset-&-run-tests (fn [tests & {:keys [reset? unlock?]
                                           :or {reset? true
                                                unlock? true}}]
                                (when reset?
                                  (doseq [{:keys [name]} devices]
                                    (instrument-device apk-path name))
                                  (Thread/sleep 4000))
                                (when unlock?
                                  (info "Unlocking screens")
                                  (doseq [{:keys [name]} devices]
                                    (unlock-device package-name name)))
                                (info "Running calabash tests")
                                (android/run-on-devices tests
                                                        devices))]
        (reset-&-run-tests calabash-tests)
        reset-&-run-tests))))


(defn run-on-emulators
  "Build a project and run tests on list of emulators"
  [emulators opts calabash-tests]
  (restart-adb)
  (info "Starting emulators")
  (start-emulators emulators)
  (wait-for-emulators emulators)
  (run-on-connected-devices opts calabash-tests))

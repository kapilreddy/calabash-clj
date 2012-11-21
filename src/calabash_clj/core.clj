(ns calabash-clj.core
  (:require [calabash-clj.build.android :as android-build]
            [calabash-clj.platforms.android :as android]))

(defn -main
  "Build an android project and run calabash queries on it."
  [project-path emulators]
  (android-build/run-on-emulators project-path emulators (fn []
                                                               (android/command "click_on_view_by_id" "button1"))))

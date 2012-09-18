(ns calabash-clj.core
  (:require [calabash-clj.build.android :as android-build]
            [calabash-clj.platforms.android :as android]))

(defn -main
  "Build an android project and run calabash queries on it."
  [project-path emulators]
  (android-build/-main project-path emulators (fn []
                                                ;; Write calabash queries here.
                                                ;; (android/touch "css" "div")
                                                )))

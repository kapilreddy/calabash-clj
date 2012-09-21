(ns calabash-clj.util
  (:require [clojure.java.shell :as shell]))

(defn run-sh
  [& commands]
  (let [op (apply shell/sh (clojure.string/split (first commands) #"\s"))]
    (if (empty? (rest commands))
      op
      (recur (rest commands)))))


(defn run-with-dir
  [dir & commands]
  (binding [shell/*sh-dir* dir]
    (apply run-sh commands)))

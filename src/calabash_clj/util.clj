(ns calabash-clj.util
  (:require [clojure.java.shell :as shell])
  (:use [clojure.java.io :only [file]])
  (:import javax.imageio.ImageIO
           javax.imageio.ImageTypeSpecifier
           javax.imageio.IIOImage))

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


(defn some-truthy
  "Check to see if the input map contains at least one of the keys ks
  The value against the key should be non-nil."
  [m & ks]
  (some #(not= (m %) nil) ks))

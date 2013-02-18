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


;;http://stackoverflow.com/questions/13063594/how-to-filter-a-directory-listing-with-a-regular-expression-in-clojure?rq=1
(defn regex-file-seq
  "Lazily filter a directory based on a regex."
  [re dir]
  (filter #(re-find re (.getPath %))
          (file-seq (clojure.java.io/file dir))))


(defn gen-gif
  [test-name]
  (let [file-pattern (re-pattern test-name)
        png-files (regex-file-seq file-pattern ".")
        buff-img (ImageIO/read (first png-files))
        spec (new ImageTypeSpecifier buff-img)
        writers (ImageIO/getImageWriters spec "GIF")
        img-writer (. writers
                      next)
        param (. img-writer
                 getDefaultWriteParam)
        meta (. img-writer
                getDefaultImageMetadata
                spec
                param)]
    (. img-writer
       setOutput
       (ImageIO/createImageOutputStream (file (format "%s.gif" test-name))))
    (. img-writer
       prepareWriteSequence
       meta)
    (doseq [png png-files]
      (. img-writer
         writeToSequence
         (new IIOImage
              (ImageIO/read png)
              nil
              nil)
         param))))

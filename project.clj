(defproject org.clojars.kapil/calabash-clj "0.1.0-SNAPSHOT"
  :description "Functional testing for Android"
  :url "https://github.com/kapilreddy/calabash-clj"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [calabash-jvm "0.0.3"]]
  :plugins [[lein-swank "1.4.4"]]
  :main calabash-clj.core)

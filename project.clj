(defproject extract-loudness "0.1.0-SNAPSHOT"
  :description "Script to compute loudness of music files"
  :url "http://el-tramo.be/extract-loudness"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [clj-logging-config "1.9.10"]
                 [org.clojure/tools.logging "0.2.6"]]
  :main ^:skip-aot extract-loudness.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

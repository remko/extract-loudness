(ns extract-loudness.core
  [:use [clojure.java.io]]
  [:use [clojure.java.shell :only [sh]]]
  [:use [clj-logging-config.log4j]]
  [:require [clojure.tools.logging :as log]]
  [:require [clojure.string :as string]]
  [:require [clojure-csv.core :as csv]]
  [:import [java.io File]]
  (:gen-class))

(set! *warn-on-reflection* true)

;; Doesn't seem to work :(
(set-logger! :pattern "%m%n")
(set-logger! :level :info) 
    
(defn itunes-music-dir []
  (first (filter #(.isDirectory (file %))
                 [(str (System/getenv "HOME") "/Music/iTunes/iTunes Media/Music")
                  (str (System/getenv "HOME") "/Music/iTunes/iTunes Music")])))

(defn shell-escape [s]
  (string/escape s {\space "\\ " \o047 "\\'" \o042 "\\\""}))

(defn collect-files [root include?]
  (filter include? (file-seq (file root))))

(defn media-file? [^File file]
  (re-matches #".*\.(mp3|m4a)" (.getName file)))

(defn parse-output [output]
  (let [matchers {:i #"(?m)^\s+I:\s+([^\s]+)\s+LUFS$"
                  :threshold #"(?m)^\s+Threshold:\s+([^\s]+)\s+LUFS$"
                  :lra #"(?m)^\s+LRA:\s+([^\s]+)\s+LU$"
                  :lra_low #"(?m)^\s+LRA low:\s+([^\s]+)\s+LUFS$"
                  :lra_high #"(?m)^\s+LRA high:\s+([^\s]+)\s+LUFS$"}
        matches (into {} (for [[k v] matchers] [k (map second (re-seq v output))]))
        matches' (assoc matches :lra_threshold (list (second (:threshold matches))))]
    (into {} (for [[k v] matches'] [k (first v)]))))

(defn extract-loudness 
  "Extracts loudness information from `filename`, and returns it as a map with
   keys :i, :threshold, :lra, :lra_threshold, :lra_low, :lra_high
   `filename` can be a file or a directory."
  [filename]
  (let [media-files (filter media-file? (file-seq (file filename)))
        cmd-input (string/join "\n" (for [f media-files] 
                                      (str "file " (shell-escape (str f)))))
        cmd-inputfile (File/createTempFile "loudness" ".in")
        cmd ["ffmpeg" "-nostats" "-f" "concat" "-i" (str cmd-inputfile)
             "-vn" "-filter_complex" "ebur128" "-f" "null" "-"]]
    (try
      (spit cmd-inputfile cmd-input)
      (let [result (apply sh cmd)]
        (when (not= (:exit result) 0)
          (log/warn "Error processing '%s'. Output: %s" filename (:err result)))
        (parse-output (:err result)))
      (finally (.delete cmd-inputfile)))))

(defn relpath [target from]
  (let [abs-target (.getAbsolutePath (file target))
        abs-from (.getAbsolutePath (file from))]
    (subs abs-target (+ (count abs-from) 1))))

(defn print-results [results root]
  (let [sorted (sort-by (comp first str) results)
        split-path (fn [^File f] 
                     (let [path (relpath f root)]
                       [(if (.isDirectory f) path (or (.getParent (file path)) "."))
                        (if (.isDirectory f) "" (.getName (file path)))]))
        rows (concat 
               [["Dir" "Name" "I" "Threshold" "LRA" "LRA Threshold" "LRA Low" "LRA High"]]
               (map (fn [[k v]] 
                      (concat 
                        (split-path k)
                        (map #(or % "ERROR")
                             ((juxt :i :threshold 
                                    :lra :lra_threshold :lra_low :lra_high) v))))
                    sorted))]
    (println (csv/write-csv rows :delimiter \;))))

(defn analyze-loudness [root]
  (let [files (collect-files root media-file?)
        job (fn [file]
              (log/infof "Processing '%s'" (relpath file root))
              [file (extract-loudness file)])
        results (into {} (pmap job files))]
    (print-results results root)))

(defn -main [& args]
  (let [target-dir (or (first args) (itunes-music-dir))]
    (log/infof "Analyzing '%s'" target-dir)
    (analyze-loudness target-dir)))

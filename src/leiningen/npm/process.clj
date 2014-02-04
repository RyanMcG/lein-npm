(ns leiningen.npm.process
  (:require [clojure.java.io :as io]))

(defn iswin
  []
  (let [os (System/getProperty "os.name")]
    (-> os .toLowerCase (.contains "windows"))))

(defn- process
  [cwd args]
  (let [fargs (if (iswin) (concat '("cmd" "/C") args) args)
        proc  (ProcessBuilder. fargs)]
    (.directory proc (io/file cwd))
    (.redirectErrorStream proc true)
    (.start proc)))

(defn exec
  [cwd args]
  (let [proc (process cwd args)]
    (io/copy (.getInputStream proc) (System/out))
    (.waitFor proc)
    (.exitValue proc)))

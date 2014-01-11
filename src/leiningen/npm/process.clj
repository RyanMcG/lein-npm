(ns leiningen.npm.process
  (:require [clojure.java.io :as io]))

(defn- process
  [cwd args]
  (let [proc (ProcessBuilder. args)]
    (.directory proc (io/file cwd))
    (.redirectErrorStream proc true)
    (.start proc)))

(defn exec
  [cwd args]
  (let [proc (process cwd args)]
    (io/copy (.getInputStream proc) (System/out))
    (.waitFor proc)
    (.exitValue proc)))

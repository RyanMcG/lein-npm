(ns leiningen.npm.node-exec
  (:require [leiningen.npm :refer [install-deps npm]]
            [clojure.java.io :as io]
            [robert.hooke :as hooke]
            [leiningen.run]))

(defn- existing-js-file-path? [path]
  (and (string? path)
       (re-matches #".*\.js$" path)
       (.exists (io/file path))))

(defn wrap-run
  [f {:keys [main] :as project} & args]
  (if (existing-js-file-path? main)
    (do
      (install-deps project)
      (apply npm project "start" args))
    (apply f project args)))

(defn install-hooks []
  (hooke/add-hook #'leiningen.run/run wrap-run))

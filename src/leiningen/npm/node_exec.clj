(ns leiningen.npm.node-exec
  (:require [leiningen.npm.process :refer [exec]]
            [leiningen.npm :refer [install-deps]]
            [clojure.java.io :as io]
            [robert.hooke :as hooke]
            [leiningen.run]))

(defn wrap-run
  [f & args]
  (let [project (first args)
        main (project :main)]
    (if (and (string? main)
             (.exists (io/file main))
             (re-matches #".*\.js$" main))
      (do
        (install-deps project)
        (exec (project :root) (concat ["node" main] (rest args))))
      (apply f args))))

(defn install-hooks []
  (hooke/add-hook #'leiningen.run/run wrap-run))

(ns leiningen.npm.deps
  (:require [cemerick.pomegranate.aether :as a]
            [leiningen.core.classpath :as cp])
  (:import [java.util.jar JarFile]))

(defn resolve-node-deps
  ([key-to-look-up project]
     (apply
      concat (project key-to-look-up)
      (for [f (a/dependency-files
               (a/resolve-dependencies
                :coordinates (project :dependencies)
                :repositories (project :repositories)))
            :let [jar (JarFile. f)
                  entry (.getEntry jar "project.clj")]
            :when entry]
        (let [project (read-string (slurp (.getInputStream jar entry)))
              project (apply hash-map (drop 3 project))]
          (project key-to-look-up)))))
  ([project]
     (resolve-node-deps :node-dependencies project)))

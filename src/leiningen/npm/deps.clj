(ns leiningen.npm.deps
  (:require [cemerick.pomegranate.aether :as a]
            [leiningen.core.classpath :as cp])
  (:import [java.util.jar JarFile]))

(defn resolve-node-deps
  [project]
  (apply
   concat (project :node-dependencies)
   (for [f (a/dependency-files
            (a/resolve-dependencies
             :coordinates (project :dependencies)
             :repositories (project :repositories)))
         :let [jar (JarFile. f)
               entry (.getEntry jar "project.clj")]
         :when entry]
     (let [project (read-string (slurp (.getInputStream jar entry)))
           project (apply hash-map (drop 3 project))]
       (project :node-dependencies)))))

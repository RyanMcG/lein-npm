(ns leiningen.npm
  (:require [leiningen.help :as help]
            [leiningen.core.main :as main]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [leiningen.npm.process :refer [exec]]
            [leiningen.npm.deps :refer [resolve-node-deps]]
            [robert.hooke]
            [leiningen.deps]))

(defn- package-file
  [project]
  (io/file (project :root) "package.json"))

(defn- environmental-consistency
  [project]
  (when (.exists (package-file project))
    (do
      (println "Your project already has a package.json file. Please remove it.")
      (main/abort)))
  (when-not (= 0 ((sh "which" "npm") :exit))
    (do
      (println "Unable to find npm on your path. Please install it.")
      (main/abort))))

(defn- invoke
  [project & args]
  (exec (project :root) (cons "npm" args)))

(defn- transform-deps
  [deps]
  (apply hash-map (flatten deps)))

(defn- project->package
  [project]
  (json/generate-string
   (merge (project :nodejs)
          {:name (project :name)
           :description (project :description)
           :version (project :version)
           :dependencies (transform-deps (resolve-node-deps project))})))

(defn- write-package
  [project]
  (doto (package-file project)
    (spit (project->package project))
    (.deleteOnExit)))

(defn- remove-package
  [project]
  (.delete (package-file project)))

(defmacro with-package
  [project & forms]
  `(try
     (write-package ~project)
     ~@forms
     (finally (remove-package ~project))))

(defn npm
  "Invoke the NPM package manager."
  ([project]
     (environmental-consistency project)
     (println (help/help-for "npm"))
     (main/abort))
  ([project & args]
     (environmental-consistency project)
     (with-package project
       (apply invoke project args))))

(defn install-deps
  [project]
  (with-package project
    (invoke project "install")))

(defn wrap-deps
  [f & args]
  (apply f args)
  (install-deps (first args)))

(defn install-hooks []
  (robert.hooke/add-hook #'leiningen.deps/deps wrap-deps))

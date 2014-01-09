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

(def ^:dynamic *npm-project-file* "package.json")
(def ^:dynamic *bower-project-file* "bower.json")
(def ^:dynamic *bower-config-file* ".bowerrc")

(defn- json-file
  [filename project]
  (io/file (project :root) filename))

(defn- environmental-consistency
  [project]
  (doseq [filename [*npm-project-file* *bower-project-file* *bower-config-file*]]
    (when (.exists (json-file filename project))
      (do
        (println
         (format "Your project already has a %s file. " filename)
         "Please remove it.")
        (main/abort))))
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
   (-> (project :nodejs)
       (merge {:name (project :name)
               :description (project :description)
               :version (project :version)
               :dependencies (transform-deps (resolve-node-deps project))})
       (assoc-in [:scripts :bower] "bower install"))))

(defn- project->component
  [project]
  (json/generate-string
   {:name (project :name)
    :description (project :description)
    :version (project :version)
    :dependencies (transform-deps
                   (resolve-node-deps :bower-dependencies project))}))

(defn- project->bowerrc
  [project]
  (json/generate-string
   {:directory (project :bower-directory)}))

(defn- write-json-file
  [filename content project]
  (doto (json-file filename project)
    (spit content)
    (.deleteOnExit)))

(defn- remove-json-file
  [filename project]
  (.delete (json-file filename project)))

(defmacro with-json-file
  [filename content project & forms]
  `(try
     (write-json-file ~filename ~content ~project)
     ~@forms
     (finally (remove-json-file ~filename ~project))))

(defn npm
  "Invoke the NPM package manager."
  ([project]
     (environmental-consistency project)
     (println (help/help-for "npm"))
     (main/abort))
  ([project & args]
     (environmental-consistency project)
     (with-json-file *npm-project-file* (project->package project) project
       (apply invoke project args))))

(defn npm-debug
  [project]
  (environmental-consistency project)
  (with-json-file *npm-project-file* (project->package project) project
    (println (str "npm project file [" *npm-project-file* "]:\n"))
    (println (slurp *npm-project-file*))))

(defn bower-debug
  [project]
  (environmental-consistency project)
  (with-json-file
    *bower-config-file* (project->bowerrc project) project
    (println (str "bower config file [" *bower-config-file* "]:\n"))
    (println (slurp *bower-config-file*))))

(defn install-deps
  [project]
  (environmental-consistency project)
  (with-json-file *npm-project-file* (project->package project) project
    (with-json-file
      *bower-project-file* (project->component project) project
      (with-json-file
        *bower-config-file* (project->bowerrc project) project
        (invoke project "install")
        (invoke project "run-script" "bower")))))

(defn wrap-deps
  [f & args]
  (apply f args)
  (install-deps (first args)))

(defn install-hooks []
  (robert.hooke/add-hook #'leiningen.deps/deps wrap-deps))

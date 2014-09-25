(ns leiningen.npm
  (:require [leiningen.help :as help]
            [leiningen.core.main :as main]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [leiningen.npm.process :refer [exec iswin]]
            [leiningen.npm.deps :refer [resolve-node-deps]]
            [robert.hooke]
            [leiningen.deps]))

(defn- root [project]
  (if-let [root (get-in [:npm :root] project)]
    (if (keyword? root)
      (project root) ;; e.g. support using :target-path
      root)
    (project :root)))

(defn- json-file
  [filename project]
  (io/file (root project) filename))

(defn- locate-npm
  []
  (if (iswin)
      (sh "cmd" "/C" "for" "%i" "in" "(npm)" "do" "@echo." "%~$PATH:i")
      (sh "which" "npm")))

(defn environmental-consistency
  [project & files]
  (doseq [filename files]
    (when (.exists (json-file filename project))
      (do
        (println
         (format "Your project already has a %s file. " filename)
         "Please remove it.")
        (main/abort))))
  (when-not (= 0 ((locate-npm) :exit))
    (do
      (println "Unable to find npm on your path. Please install it.")
      (main/abort))))

(defn- invoke
  [project & args]
  (let [return-code (exec (root project) (cons "npm" args))]
    (when (> return-code 0)
      (main/exit return-code))))

(defn transform-deps
  [deps]
  (apply hash-map (flatten deps)))

(defn- project->package
  [project]
  (json/generate-string
   (merge {:private true} ;; prevent npm warnings about repository and README
          {:name (project :name)
           :description (project :description)
           :version (project :version)
           :dependencies (transform-deps (resolve-node-deps project))}
          (when-let [main (project :main)]
            {:scripts {:start (str "node " main)}})
          (get-in [:npm :package] project))
   {:pretty true}))

(defn write-json-file
  [filename content project]
  (doto (json-file filename project)
    (spit content)
    (.deleteOnExit)))

(defn remove-json-file
  [filename project]
  (.delete (json-file filename project)))

(defmacro with-json-file
  [filename content project & forms]
  `(try
     (write-json-file ~filename ~content ~project)
     ~@forms
     (finally (remove-json-file ~filename ~project))))

(defn npm-debug
  [project filename]
  (with-json-file filename (project->package project) project
    (println "lein-npm generated package.json:\n")
    (println (slurp (json-file filename project)))))

(defn npm
  "Invoke the npm package manager."
  ([project]
     (environmental-consistency project "package.json")
     (println (help/help-for "npm"))
     (main/abort))
  ([project & args]
     (environmental-consistency project "package.json")
     (cond
      (= ["pprint"] args)
      (npm-debug project "package.json")
      :else
      (with-json-file "package.json" (project->package project) project
        (apply invoke project args)))))

(defn install-deps
  [project]
  (environmental-consistency project)
  (with-json-file "package.json" (project->package project) project
    (invoke project "install")))

(defn wrap-deps
  [f & args]
  (apply f args)
  (install-deps (first args)))

(defn install-hooks []
  (robert.hooke/add-hook #'leiningen.deps/deps wrap-deps))

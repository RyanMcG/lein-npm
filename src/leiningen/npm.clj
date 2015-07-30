(ns leiningen.npm
  (:require [leiningen.help :as help]
            [leiningen.core.main :as main]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.java.shell :refer [sh]]
            [leiningen.npm.process :refer [exec iswin]]
            [leiningen.npm.deps :refer [resolve-node-deps]]
            [robert.hooke]
            [leiningen.deps]))

(defn- root [project]
  (if-let [root (get-in project [:npm :root])]
    (if (keyword? root)
      (project root) ;; e.g. support using :target-path
      root)
    (project :root)))

(defn- project-file
  [filename project]
  (io/file (root project) filename))

(def ^:const package-file-name  "package.json")

(defn- package-file-from-project [p] (project-file package-file-name p))

(defn- locate-npm
  []
  (if (iswin)
      (sh "cmd" "/C" "for" "%i" "in" "(npm)" "do" "@echo." "%~$PATH:i")
      (sh "which" "npm")))

(defn environmental-consistency
  [project]
  (when (.exists (package-file-from-project project))
    (do
      (println
        (format "Your project already has a %s file. " package-file-name)
        "Please remove it.")
      (main/abort)))
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
          (get-in project [:npm :package]))
   {:pretty true}))

(defn- write-ephemeral-file
  [file content]
  (doto file
    (-> .getParentFile .mkdirs)
    (spit content)
    (.deleteOnExit)))

(defmacro with-ephemeral-file
  [file content & forms]
  `(try
     (write-ephemeral-file ~file ~content)
     ~@forms
     (finally (.delete ~file))))

(defmacro with-ephemeral-package-json [project & body]
  `(with-ephemeral-file (package-file-from-project ~project)
                        (project->package ~project)
     ~@body))

(defn npm-debug
  [project]
  (with-ephemeral-package-json project
    (println "lein-npm generated package.json:\n")
    (println (slurp (package-file-from-project project)))))

(def key-deprecations
  "Mappings from old keys to new keys in :npm."
  {:nodejs :package
   :node-dependencies :dependencies
   :npm-root :root})

(def deprecated-keys (set (keys key-deprecations)))

(defn select-deprecated-keys
  "Returns a set of deprecated keys present in the given project."
  [project]
  (set/difference deprecated-keys
                  (set/difference deprecated-keys
                                  (set (keys project)))))

(defn- generate-deprecation-warning [used-key]
  (str used-key " is deprecated. Use " (key-deprecations used-key)
       " in an :npm map instead."))

(defn warn-about-deprecation [project]
  (if-let [used-deprecated-keys (seq (select-deprecated-keys project))]
    (doseq [dk used-deprecated-keys]
      (main/warn "WARNING:" (generate-deprecation-warning dk)))))

(defn npm
  "Invoke the npm package manager."
  ([project]
     (environmental-consistency project)
     (warn-about-deprecation project)
     (println (help/help-for "npm"))
     (main/abort))
  ([project & args]
     (environmental-consistency project)
     (warn-about-deprecation project)
     (cond
      (= ["pprint"] args)
      (npm-debug project)
      :else
      (with-ephemeral-package-json project
        (apply invoke project args)))))

(defn install-deps
  [project]
  (environmental-consistency project)
  (warn-about-deprecation project)
  (with-ephemeral-package-json project
    (invoke project "install")))

; Only run install-deps via wrap-deps once. For some reason it is being called
; multiple times with when using `lein deps` and I cannot determine why.
(defonce install-locked (atom false))

(defn wrap-deps
  [f & args]
  (if @install-locked
    (apply f args)
    (do
      (reset! install-locked true)
      (let [ret (apply f args)]
        (install-deps (first args))
        (reset! install-locked false)
        ret))))

(defn install-hooks []
  (robert.hooke/add-hook #'leiningen.deps/deps wrap-deps))

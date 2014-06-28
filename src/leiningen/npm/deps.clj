(ns leiningen.npm.deps
  (:require [cemerick.pomegranate.aether :as a]
            [clojure.java.io :as io]
            [leiningen.core.classpath :as cp]
            [leiningen.core.project :as project])
  (:import [java.util.jar JarFile]))

(defn- get-checkouts-project-file [root dep]
  (io/file root "checkouts" dep "project.clj"))

;; Based on technomany/leiningen:leiningen-core/src/leiningen/core/classpath.clj#read-dependency-project
(defn- read-checkouts-project
  "Reads in the contents of a project.clj file for a given checkouts
  dependency, returning a Clojure data structure representation of the
  project configuration."
  [root dep]
  (let [checkouts-project-file (get-checkouts-project-file root dep)]
    (if (.exists checkouts-project-file)
      (let [checkouts-project (.getAbsolutePath checkouts-project-file)]
        (try (project/read checkouts-project [:default])
             (catch Exception e
               (throw (Exception. (format "Problem loading %s" checkouts-project) e)))))
      (println
       "WARN ignoring checkouts directory" dep
       "as it does not contain a project.clj file."))))

(defn- scan-checkouts-projects
  "Searches for project.clj files under the given root path, and
  returns a set of project names that it finds."
  [root]
  (-> (io/file root "checkouts")
      (.list)
      (->> (keep (partial read-checkouts-project root))
           (keep (comp name :name))
           (set))))


(defn- resolve-in-jar-dep
  "Resolves a given lookup-key in the project definiton in a given
  jar-file, if a project.clj is found in it. Nil when there are no
  dependencies, or when the jar's project is in the given exclusions
  set."
  [lookup-key exclusions jar-file]
  (let [jar-project-entry (.getEntry jar-file "project.clj")
        jar-project-src (when jar-project-entry
                          (read-string (slurp (.getInputStream jar-file jar-project-entry))))
        jar-project-map (when jar-project-src
                          (->> jar-project-src (drop 3) (apply hash-map)))
        jar-project-name (when jar-project-src
                           (name (second jar-project-src)))
        jar-project-deps (when jar-project-map
                           (jar-project-map lookup-key))]
    (when (not (contains? exclusions jar-project-name))
      jar-project-deps)))

(defn resolve-repositories [project]
  (->> (:repositories project)
       (map (fn [[name repo]]
              [name (leiningen.core.user/resolve-credentials repo)]))
       (into {})))

(defn- resolve-in-jar-deps
  "Resolves a given lookup-key in all the project definitions for jar
  dependencies of a project. Excludes any Clojure project jars that
  are named in a set of exclusions."
  [lookup-key project exclusions]
  (->> (a/resolve-dependencies :coordinates (project :dependencies)
                               :repositories (resolve-repositories (project :repositories)))
       (a/dependency-files)
       (map #(JarFile. %))
       (keep (partial resolve-in-jar-dep lookup-key exclusions))
       (reduce concat)))

(defn- resolve-in-checkouts-deps
  "Resolves a given lookup-key in all the project.clj definitions for
  checkouts dependencies under a given project root."
  [lookup-key root]
  (-> (io/file root "checkouts")
      (.list)
      (->> (keep (partial read-checkouts-project root))
           (keep lookup-key)
           (reduce concat))))

(defn resolve-node-deps
  ([lookup-key project]
     (let [deps (concat (project lookup-key)
                        (resolve-in-jar-deps lookup-key project (scan-checkouts-projects (:root project)))
                        (resolve-in-checkouts-deps lookup-key (:root project)))]
       deps))
  ([project]
     (resolve-node-deps :node-dependencies project)))

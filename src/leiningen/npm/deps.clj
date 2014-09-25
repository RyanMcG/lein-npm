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

(defn- find-project-form
  "Find the first form where the first element is the symbol defproject in the
  given top-level forms."
  [forms]
  (letfn [(fpf [form]
            (if (list? form)
              (if (= 'defproject (first form))
                form
                (find-project-form form))))]
    (->> forms
         (map fpf)
         (keep identity)
         (first))))

(defn- read-project-form [input-stream]
  (->> (str \( (slurp input-stream) \))
       (read-string)
       (find-project-form)))

(defn- resolve-in-jar-dep
  "Finds dependencies in the project definiton in a given jar-file using
  lookup-deps, if a project.clj is found in it. Nil when there are no
  dependencies, or when the jar's project is in the given exclusions set."
  [lookup-deps exclusions jar-file]
  (let [jar-project-entry (.getEntry jar-file "project.clj")
        jar-project-src (when jar-project-entry
                          (-> jar-file
                              (.getInputStream jar-project-entry)
                              (read-project-form)))
        jar-project-map (when jar-project-src
                          (->> jar-project-src (drop 3) (apply hash-map)))
        jar-project-name (when jar-project-src
                           (name (second jar-project-src)))
        jar-project-deps (when jar-project-map
                           (lookup-deps jar-project-map))]
    (when (not (contains? exclusions jar-project-name))
      jar-project-deps)))

(defn resolve-repositories [repos]
  (->> repos
       (map (fn [[name repo]]
              [name (leiningen.core.user/resolve-credentials repo)]))
       (into {})))

(defn- resolve-in-jar-deps
  "Finds dependencies in a project definition using lookup-deps in all the
  project definitions for jar dependencies of a project. Excludes any Clojure
  project jars that are named in a set of exclusions."
  [lookup-deps project exclusions]
  (->> (a/resolve-dependencies :coordinates (project :dependencies)
                               :repositories (resolve-repositories (project :repositories)))
       (a/dependency-files)
       (map #(JarFile. %))
       (keep (partial resolve-in-jar-dep lookup-deps exclusions))
       (reduce concat)))

(defn- resolve-in-checkouts-deps
  "Finds dependencies, using lookup-deps, in all the project.clj definitions for
  checkouts dependencies under a given project root."
  [lookup-deps root]
  (-> (io/file root "checkouts")
      (.list)
      (->> (keep (partial read-checkouts-project root))
           (keep lookup-deps)
           (reduce concat))))

(defn resolve-node-deps
  ([lookup-deps project]
     (let [deps (concat (lookup-deps project)
                        (resolve-in-jar-deps lookup-deps project (scan-checkouts-projects (:root project)))
                        (resolve-in-checkouts-deps lookup-deps (:root project)))]
       deps))
  ([project]
     (resolve-node-deps :node-dependencies project)))

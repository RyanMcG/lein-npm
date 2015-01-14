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
  "Resolves a given lookup-key in the project definiton in a given
  jar-file, if a project.clj is found in it. Nil when there are no
  dependencies, or when the jar's project is in the given exclusions
  set."
  [lookup-key exclusions jar-file]
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
                           (jar-project-map lookup-key))]
    (when (not (contains? exclusions jar-project-name))
      jar-project-deps)))

(defn- get-non-proxy-hosts []
  (let [system-no-proxy (System/getenv "no_proxy")
        lein-no-proxy (System/getenv "http_no_proxy")]
    (if (and (empty? lein-no-proxy) (not-empty system-no-proxy))
      (->> (str/split system-no-proxy #",")
           (map #(str "*" %))
           (str/join "|"))
      (System/getenv "http_no_proxy"))))

(defn- get-proxy-settings
  "Returns a map of the JVM proxy settings"
  ([] (get-proxy-settings "http_proxy"))
  ([key]
     (if-let [proxy (System/getenv key)]
       (let [url (utils/build-url proxy)
             user-info (.getUserInfo url)
             [username password] (and user-info (.split user-info ":"))]
         {:host (.getHost url)
          :port (.getPort url)
          :username username
          :password password
          :non-proxy-hosts (get-non-proxy-hosts)}))))

(defn- resolve-in-jar-deps
  "Resolves a given lookup-key in all the project definitions for jar
  dependencies of a project. Excludes any Clojure project jars that
  are named in a set of exclusions."
  [lookup-key project exclusions]
  (->> (a/resolve-dependencies :coordinates (project :dependencies)
                               :repositories (project :repositories)
                               :proxy (get-proxy-settings))
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

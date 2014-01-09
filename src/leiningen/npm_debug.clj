(ns leiningen.npm-debug
  (:require [leiningen.help :as help]
            [leiningen.core.main :as main]
            [leiningen.npm :as npm]))

(defn npm-debug
  "Show debugging information for the npm task."
  ([project]
     (npm/npm-debug project))
  ([project & args]
     (if (= "bower" (first args))
       (npm/bower-debug project)
       (npm-debug project))))

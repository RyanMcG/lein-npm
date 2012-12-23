(ns lein-npm.plugin
  (:require [leiningen.npm :as npm]
            [leiningen.npm.node-exec :as exec]))

(defn hooks []
  (npm/install-hooks)
  (exec/install-hooks))

(ns leiningen.npm-test
  (:require [leiningen.npm :refer :all]
            [fixturex.context :refer :all]
            [clojure.test :refer :all]))

(deftest-ctx test-deprecation-warnings
  [:project {}
   :wad #(with-out-str
           (with-redefs [leiningen.core.main/warn println]
             (warn-about-deprecation project)))]
  (letfn [(warns [msg] (is (= (wad) msg)))
          (deprecated [ks] (is (= (select-deprecated-keys project) ks)))]
    (testing "without keys"
      (deprecated #{})
      (warns ""))
    (testing-ctx "with an unimportant key" [:project {:unimportant-key 1}]
      (deprecated #{})
      (warns ""))
    (doseq [dk deprecated-keys]
      (testing-ctx (str "with deprecated key: " dk) [:project {dk :anything}]
        (deprecated #{dk})
        (warns (str "WARNING: " dk " is deprecated. Use " (key-deprecations dk)
               " in an :npm map instead.\n"))))))

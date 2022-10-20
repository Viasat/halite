;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-composition
  (:require [com.viasat.halite.propagate :as hp])
  (:use clojure.test))

(deftest test-top-abstract
  (is (= {:$in #:ws{:B {:$refines-to #:ws{:A {}}}
                    :C {:$refines-to #:ws{:A {}}}}
          :$refines-to #:ws{:A {}}}
         (hp/propagate '{:ws/X
                         {:spec-vars {:a :ws/A}
                          :constraints []
                          :refines-to {}}

                         :ws/A
                         {:abstract? true
                          :spec-vars {}
                          :constraints []
                          :refines-to {}}

                         :ws/B
                         {:spec-vars {}
                          :constraints []
                          :refines-to {:ws/A {:expr {:$type :ws/A}}}}

                         :ws/C
                         {:spec-vars {}
                          :constraints []
                          :refines-to {:ws/A {:expr {:$type :ws/A}}}}}

                       {:$refines-to {:ws/A {}}}))))

;; (clojure.test/run-tests)

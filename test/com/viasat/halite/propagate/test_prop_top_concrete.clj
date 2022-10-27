;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-top-concrete
  (:require [com.viasat.halite.propagate :as hp])
  (:use clojure.test)
  (:import [clojure.lang ExceptionInfo]
           [org.chocosolver.solver.exception ContradictionException]))

(deftest test-top-level-refines-to-concrete-spec
  (is (= {:$type :ws/A
          :$refines-to {}}
         (hp/propagate '{:ws/A {}}
                       {:$refines-to {:ws/A {}}}))))

(deftest test-top-level-refines-to-concrete-spec-with-refinement
  (is (= {:$in {:ws/A {:$refines-to {}}
                :ws/B {:$refines-to {:ws/A {}}}}
          :$refines-to {}}
         (hp/propagate '{:ws/A {}
                         :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}}
                       {:$refines-to {:ws/A {}}}))))

(deftest test-top-level-refines-to-concrete-spec-with-refinement-abstract-in-path
  (is (= {:$in {:ws/A {:$refines-to {}}
                :ws/B {:$refines-to {:ws/A {}}}
                :ws/D {:$refines-to {:ws/C {}
                                     :ws/B {}
                                     :ws/A {}}}}
          :$refines-to {}}
         (hp/propagate '{:ws/A {}
                         :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}
                         :ws/C {:abstract? true
                                :refines-to {:ws/B {:expr {:$type :ws/B}}}}
                         :ws/D {:refines-to {:ws/C {:expr {:$type :ws/C}}}}}
                       {:$refines-to {:ws/A {}}}))))

(deftest test-top-level-refines-to-multiple-concrete-spec-with-refinement
  (is (= {:$in {;; ideally :ws/A should not be included in results, but lower layer needs to address this
                :ws/A {:$refines-to {}}
                :ws/B {:$refines-to {:ws/A {}}}}
          :$refines-to {}}
         (hp/propagate '{:ws/A {}
                         :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}}
                       {:$refines-to {:ws/A {}
                                      :ws/B {}}}))))

(deftest test-refine-to-abstract-and-concrete
  (is (= ;; ideally this result would be "nothing", but lower layer needs to address this
       {:$in {:ws/A {:$refines-to {}}
              :ws/B {:$refines-to {:ws/A {}}}}
        :$refines-to {}}
       (hp/propagate '{:ws/A {}
                       :ws/X {}
                       :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}}
                     {:$refines-to {:ws/A {}
                                    :ws/X {}}})))

  (is (= {:$in {:ws/C {:$refines-to {:ws/A {}
                                     :ws/X {}}}
                :ws/X {:$refines-to {}}}
          :$refines-to {}}
         (hp/propagate '{:ws/A {}
                         :ws/X {}
                         :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}
                         :ws/C {:refines-to {:ws/A {:expr {:$type :ws/A}}
                                             :ws/X {:expr {:$type :ws/X}}}}}
                       {:$refines-to {:ws/A {}
                                      :ws/X {}}}))))

;; (clojure.test/run-tests)

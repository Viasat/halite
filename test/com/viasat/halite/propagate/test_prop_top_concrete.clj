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
  (is (= {:$type :ws/B
          :$refines-to {:ws/A {}}}
         (hp/propagate '{:ws/A {}
                         :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}}
                       {:$refines-to {:ws/A {}
                                      :ws/B {}}}))))

(deftest test-refine-to-abstract-and-concrete
  (is (thrown-with-msg? ExceptionInfo #"empty domain"
                        ;; ideally this would be a constraint violation exception
                        (hp/propagate '{:ws/A {}
                                        :ws/X {}
                                        :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}}
                                      {:$refines-to {:ws/A {}
                                                     :ws/X {}}})))

  (is (= {:$type :ws/C
          :$refines-to {:ws/A {}
                        :ws/X {}}}
         (hp/propagate '{:ws/A {}
                         :ws/X {}
                         :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}
                         :ws/C {:refines-to {:ws/A {:expr {:$type :ws/A}}
                                             :ws/X {:expr {:$type :ws/X}}}}}
                       {:$refines-to {:ws/A {}
                                      :ws/X {}}}))))

(deftest test-others
  (is (thrown-with-msg? ExceptionInfo #"No concrete specs refine to"
                        (hp/propagate '{:ws/P {:abstract? true}
                                        :ws/X {:abstract? true}}
                                      {:$refines-to {:ws/X {} :ws/P {}}})))

  (is (thrown-with-msg? ExceptionInfo #"empty domain"
                        ;; ideally this would be a constraint violation exception
                        (hp/propagate '{:ws/A {:abstract? true}
                                        :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}
                                        :ws/C {:refines-to {:ws/A {:expr {:$type :ws/A}}}}
                                        :ws/D {:refines-to {:ws/C {:expr {:$type :ws/C}}}}
                                        :ws/X {:abstract? true}
                                        :ws/Y {:refines-to {:ws/X {:expr {:$type :ws/X}}}}
                                        :ws/Z {:refines-to {:ws/X {:expr {:$type :ws/X}}}}
                                        :ws/P {:abstract? true}}
                                      {:$refines-to {:ws/X {} :ws/A {}}})))

  (is (= {:$type :ws/Q
          :$refines-to {:ws/A {}
                        :ws/X {}}}
         (hp/propagate '{:ws/A {:abstract? true}
                         :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}
                         :ws/X {:abstract? true}
                         :ws/Y {:refines-to {:ws/X {:expr {:$type :ws/X}}}}
                         :ws/Q {:refines-to {:ws/A {:expr {:$type :ws/A}}
                                             :ws/X {:expr {:$type :ws/X}}}}}
                       {:$refines-to {:ws/A {} :ws/X {}}}))))

;; (clojure.test/run-tests)

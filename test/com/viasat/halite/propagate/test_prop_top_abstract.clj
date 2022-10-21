;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-composition
  (:require [com.viasat.halite.propagate :as hp])
  (:use clojure.test)
  (:import [clojure.lang ExceptionInfo]
           [org.chocosolver.solver.exception ContradictionException]))

(deftest test-top-level-refines-to-bound
  (is (= {:$in #:ws{:B {:$refines-to #:ws{:A {}}}
                    :C {:$refines-to #:ws{:A {}}}}
          :$refines-to #:ws{:A {}}}
         (hp/propagate '{:ws/A {:abstract? true
                                :spec-vars {}
                                :constraints []
                                :refines-to {}}

                         :ws/B {:spec-vars {}
                                :constraints []
                                :refines-to {:ws/A {:expr {:$type :ws/A}}}}

                         :ws/C {:spec-vars {}
                                :constraints []
                                :refines-to {:ws/A {:expr {:$type :ws/A}}}}}

                       {:$refines-to {:ws/A {}}}))))

(deftest test-top-level-refines-to-bound-multiple
  (is (= {:$in {:ws/Q {:$refines-to #:ws{:ws/A {}
                                         :ws/C {}
                                         :ws/X {}
                                         :ws/Y {}}}}
          :$refines-to {:ws/A {}
                        :ws/C {}
                        :ws/X {}
                        :ws/Y {}}}
         (hp/propagate '{:ws/A {:abstract? true
                                :spec-vars {}
                                :constraints []
                                :refines-to {}}

                         :ws/B {:spec-vars {}
                                :constraints []
                                :refines-to {:ws/A {:expr {:$type :ws/A}}}}

                         :ws/C {:spec-vars {}
                                :constraints []
                                :refines-to {:ws/A {:expr {:$type :ws/A}}}}

                         :ws/X {:abstract? true
                                :spec-vars {}
                                :constraints []
                                :refines-to {}}

                         :ws/Y {:spec-vars {}
                                :constraints []
                                :refines-to {:ws/X {:expr {:$type :ws/X}}}}

                         :ws/Q {:spec-vars {}
                                :constraints []
                                :refines-to {:ws/Y {:expr {:$type :ws/Y}}
                                             :ws/C {:expr {:$type :ws/C}}}}}

                       {:$refines-to {:ws/A {}
                                      :ws/X {}}}))))

(deftest test-top-level-refines-to-bound-multiple-no-match
  (is (= :contradiction
         (try (hp/propagate '{:ws/A {:abstract? true
                                     :spec-vars {}
                                     :constraints []
                                     :refines-to {}}

                              :ws/B {:spec-vars {}
                                     :constraints []
                                     :refines-to {:ws/A {:expr {:$type :ws/A}}}}

                              :ws/C {:spec-vars {}
                                     :constraints []
                                     :refines-to {:ws/A {:expr {:$type :ws/A}}}}

                              :ws/X {:abstract? true
                                     :spec-vars {}
                                     :constraints []
                                     :refines-to {}}

                              :ws/Y {:spec-vars {}
                                     :constraints []
                                     :refines-to {:ws/X {:expr {:$type :ws/X}}}}}

                            {:$refines-to {:ws/A {}
                                           :ws/X {}}})
              (catch ContradictionException _
                :contradiction))))

  (is (thrown-with-msg? ExceptionInfo #"No concrete specs"
                        (hp/propagate '{:ws/A {:abstract? true
                                               :spec-vars {}
                                               :constraints []
                                               :refines-to {}}

                                        :ws/X {:abstract? true
                                               :spec-vars {}
                                               :constraints []
                                               :refines-to {}}}

                                      {:$refines-to {:ws/A {}
                                                     :ws/X {}}}))))

(deftest test-top-abstract-refines-to-concrete
  #_(is (= {:$in #:ws{:B {:$refines-to #:ws{:A {}}}
                      :C {:$refines-to #:ws{:A {}}}}
            :$refines-to #:ws{:A {}}}
           (hp/propagate '{:ws/A
                           {:spec-vars {}
                            :constraints []
                            :refines-to {}}

                           :ws/B
                           {:spec-vars {}
                            :constraints []
                            :refines-to {:ws/A {:expr {:$type :ws/A}}}}}

                         {:$refines-to {:ws/A {}}}))))

;; (clojure.test/run-tests)

;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-abstract
  (:require [com.viasat.halite.propagate :as propagate])
  (:use clojure.test)
  (:import [clojure.lang ExceptionInfo]
           [org.chocosolver.solver.exception ContradictionException]))

(deftest test-top-level-refines-to-bound
  (is (= {:$in {:ws/B {:$refines-to {:ws/A {}}}
                :ws/C {:$refines-to {:ws/A {}}}}
          :$refines-to {:ws/A {}}}
         (propagate/propagate '{:ws/A {:abstract? true}

                                :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}

                                :ws/C {:refines-to {:ws/A {:expr {:$type :ws/A}}}}}

                              {:$refines-to {:ws/A {}}}))))

(deftest test-top-level-refines-to-bound-multiple
  (is (= {:$type :ws/Q
          :$refines-to {:ws/Y {} :ws/C {} :ws/X {} :ws/A {}}}
         (propagate/propagate '{:ws/A {:abstract? true}

                                :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}

                                :ws/C {:refines-to {:ws/A {:expr {:$type :ws/A}}}}

                                :ws/X {:abstract? true}

                                :ws/Y {:refines-to {:ws/X {:expr {:$type :ws/X}}}}

                                :ws/Q {:refines-to {:ws/Y {:expr {:$type :ws/Y}}
                                                    :ws/C {:expr {:$type :ws/C}}}}}

                              {:$refines-to {:ws/A {}
                                             :ws/X {}}}))))

(deftest test-top-level-refines-to-bound-multiple-some-concrete
  (is (= {:$type :ws/Q
          :$refines-to {:ws/Y {} :ws/C {} :ws/X {} :ws/A {}}}
         (propagate/propagate '{:ws/A {:abstract? true}

                                :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}

                                :ws/C {:refines-to {:ws/A {:expr {:$type :ws/A}}}}

                                :ws/X {}

                                :ws/Y {:refines-to {:ws/X {:expr {:$type :ws/X}}}}

                                :ws/Q {:refines-to {:ws/Y {:expr {:$type :ws/Y}}
                                                    :ws/C {:expr {:$type :ws/C}}}}}

                              {:$refines-to {:ws/A {}
                                             :ws/X {}}})))

  (is (= {:$type :ws/Q
          :$refines-to {:ws/Y {} :ws/C {} :ws/X {} :ws/A {}}}
         (propagate/propagate '{:ws/A {}

                                :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}

                                :ws/C {:refines-to {:ws/A {:expr {:$type :ws/A}}}}

                                :ws/X {:abstract? true}

                                :ws/Y {:refines-to {:ws/X {:expr {:$type :ws/X}}}}

                                :ws/Q {:refines-to {:ws/Y {:expr {:$type :ws/Y}}
                                                    :ws/C {:expr {:$type :ws/C}}}}}

                              {:$refines-to {:ws/A {}
                                             :ws/X {}}}))))

(deftest test-top-level-refines-to-bound-multiple-with-fields
  #_(is (= {:$in {:ws/Q {:$refines-to {:ws/Y {:y {:$in [120 130]}}
                                       :ws/B {:b {:$in [120 130]}}
                                       :ws/X {:x {:$in [120 130]}}
                                       :ws/A {:a {:$in [120 130]}}}}}
            :$refines-to {:ws/A {:a {:$in [120 130]}}
                          :ws/B {:b {:$in [120 130]}}
                          :ws/Y {:y {:$in [120 130]}}
                          :ws/X {:x {:$in [120 130]}}}}
           (propagate/propagate '{:ws/A {:abstract? true
                                         :spec-vars {:a "Integer"}
                                         :constraints [["ac" (< a 200)]]}

                                  :ws/B {:spec-vars {:b "Integer"}
                                         :refines-to {:ws/A {:expr {:$type :ws/A
                                                                    :a b}}}}

                                  :ws/X {:abstract? true
                                         :spec-vars {:x "Integer"}
                                         :constraints [["xc" (> x 100)]]}

                                  :ws/Y {:spec-vars {:y "Integer"}
                                         :refines-to {:ws/X {:expr {:$type :ws/X
                                                                    :x y}}}}

                                  :ws/Q {:spec-vars {:q "Integer"}
                                         :refines-to {:ws/Y {:expr {:$type :ws/Y
                                                                    :y q}}
                                                      :ws/B {:expr {:$type :ws/B
                                                                    :b q}}}}}

                                {:$refines-to {:ws/A {:a {:$in [0 1300]}}
                                               :ws/X {:x {:$in [0 1300]}}}}))))

(deftest test-top-level-refines-to-bound-multiple-with-fields-inverted
  #_(is (= {:$in {:ws/Q {:q {:$in [120 130]}
                         :$refines-to {:ws/Y {:y {:$in [120 130]}}
                                       :ws/B {:b {:$in [120 130]}}
                                       :ws/X {:x {:$in [120 130]}}
                                       :ws/A {:a {:$in [120 130]}}}}}
            :$refines-to {:ws/A {:a {:$in [120 130]}}
                          :ws/B {:b {:$in [120 130]}}
                          :ws/Y {:y {:$in [120 130]}}
                          :ws/X {:x {:$in [120 130]}}}}
           (propagate/propagate '{:ws/A {:abstract? true
                                         :spec-vars {:a "Integer"}
                                         :constraints [["ac" (< a 200)]]}

                                  :ws/B {:spec-vars {:b "Integer"}
                                         :refines-to {:ws/A {:expr {:$type :ws/A
                                                                    :a b}}}}

                                  :ws/X {:abstract? true
                                         :spec-vars {:x "Integer"}
                                         :constraints [["xc" (> x 100)]]}

                                  :ws/Y {:spec-vars {:y "Integer"}
                                         :refines-to {:ws/X {:expr {:$type :ws/X
                                                                    :x y}}}}

                                  :ws/Q {:spec-vars {:q "Integer"}
                                         :refines-to {:ws/Y {:expr {:$type :ws/Y
                                                                    :y q}}
                                                      :ws/B {:expr {:$type :ws/B
                                                                    :b q}}}}}

                                {:$refines-to {:ws/A {:a {:$in [120 130]}}
                                               :ws/X {:x {:$in [120 130]}}}}))))

(deftest test-top-level-refines-to-bound-multiple-with-fields-inverted-merge-maybes
  #_(is (= {:$in {:ws/Q {:q {:$in [120 130]}
                         :$refines-to {:ws/Y {:y {:$in [120 130]}}
                                       :ws/B {:b {:$in [120 130]}}
                                       :ws/X {:x {:$in [120 130]}}
                                       :ws/A {:a {:$in [120 130]}}}}}
            :$refines-to {:ws/A {:a {:$in [120 130]}}
                          :ws/B {:b {:$in [120 130]}}
                          :ws/Y {:y {:$in [120 130]}}
                          :ws/X {:x {:$in [120 130]}}}}
           (propagate/propagate '{:ws/A {:abstract? true
                                         :spec-vars {:a "Integer"}
                                         :constraints [["ac" (< a 200)]]}

                                  :ws/B {:spec-vars {:b "Integer"}
                                         :refines-to {:ws/A {:expr {:$type :ws/A
                                                                    :a b}}}}

                                  :ws/X {:abstract? true
                                         :spec-vars {:x "Integer"}
                                         :constraints [["xc" (> x 100)]]}

                                  :ws/Y {:spec-vars {:y "Integer"}
                                         :refines-to {:ws/X {:expr {:$type :ws/X
                                                                    :x y}}}}

                                  :ws/Q {:spec-vars {:q "Integer"
                                                     :p [:Maybe :ws/P]}
                                         :refines-to {:ws/Y {:expr {:$type :ws/Y
                                                                    :y (if-value p
                                                                                 200
                                                                                 q)}}
                                                      :ws/B {:expr {:$type :ws/B
                                                                    :b q}}}}
                                  :ws/P {}}

                                {:$refines-to {:ws/A {:a {:$in [120 130]}}
                                               :ws/X {:x {:$in [120 130]}}}}))))

(deftest test-top-level-refines-to-bound-multiple-no-match
  #_(is (= :contradiction
           (try (propagate/propagate '{:ws/A {:abstract? true}

                                       :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}

                                       :ws/C {:refines-to {:ws/A {:expr {:$type :ws/A}}}}

                                       :ws/X {:abstract? true}

                                       :ws/Y {:refines-to {:ws/X {:expr {:$type :ws/X}}}}}

                                     {:$refines-to {:ws/A {}
                                                    :ws/X {}}})
                (catch ContradictionException _
                  :contradiction))))

  #_(is (thrown-with-msg? ExceptionInfo #"No concrete specs"
                          (propagate/propagate '{:ws/A {:abstract? true
                                                        :spec-vars {}
                                                        :constraints []
                                                        :refines-to {}}

                                                 :ws/X {:abstract? true
                                                        :spec-vars {}
                                                        :constraints []
                                                        :refines-to {}}}

                                               {:$refines-to {:ws/A {}
                                                              :ws/X {}}}))))

(deftest test-with-maybe-type
  #_(is (= {:$type :ws/B
            :$refines-to {:ws/A {:x {:$type [:Maybe :ws/X]
                                     :y {:$in [-1000 1000]}}}}})
        (propagate/propagate '{:ws/A {:abstract? true
                                      :spec-vars {:x [:Maybe :ws/X]}}

                               :ws/B {:spec-vars {:b "Integer"}
                                      :refines-to {:ws/A {:expr {:$type :ws/A
                                                                 :x (when (> b 0)
                                                                      {:$type :ws/A
                                                                       :y b})}}}}

                               :ws/P {:abstract? true
                                      :refines-to {:ws/A {:expr {:$type :ws/A}}}}

                               :ws/X {:spec-vars {:y "Integer"}}}

                             {:$refines-to {:ws/A {}
                                            :ws/P {}}})))

(deftest test-top-abstract-refines-to-concrete
  #_(is (= {:$in #:ws{:B {:$refines-to #:ws{:A {}}}
                      :C {:$refines-to #:ws{:A {}}}}
            :$refines-to #:ws{:A {}}}
           (propagate/propagate '{:ws/A {}

                                  :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}}

                                {:$refines-to {:ws/A {}}}))))

;; (clojure.test/run-tests)

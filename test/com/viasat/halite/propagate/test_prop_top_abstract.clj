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
                                         :ws/Y {}}}
                ;; ideally the following two entries would not appear, to be addressed at a lower layer
                :ws/B {:$refines-to {:ws/A {}}}
                :ws/C {:$refines-to {:ws/A {}}}}
          :$refines-to {:ws/A {}
                        ;; ideally the following would appear (?), to be addressed at a lower layer
                        ;;:ws/C {}
                        ;;:ws/X {}
                        ;;:ws/Y {}
                        }}
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

(deftest test-top-level-refines-to-bound-multiple-with-fields
  #_(is (= {:$in {:ws/Q {:$refines-to {:ws/Y {:y {:$in [120 130]}}
                                       :ws/B {:b {:$in [120 130]}}
                                       :ws/X {:x {:$in [120 130]}}
                                       :ws/A {:a {:$in [120 130]}}}}}
            :$refines-to {:ws/A {:a {:$in [120 130]}}
                          :ws/B {:b {:$in [120 130]}}
                          :ws/Y {:y {:$in [120 130]}}
                          :ws/X {:x {:$in [120 130]}}}}
           (hp/propagate '{:ws/A {:abstract? true
                                  :spec-vars {:a "Integer"}
                                  :constraints [["ac" (< a 200)]]
                                  :refines-to {}}

                           :ws/B {:spec-vars {:b "Integer"}
                                  :constraints []
                                  :refines-to {:ws/A {:expr {:$type :ws/A
                                                             :a b}}}}

                           :ws/X {:abstract? true
                                  :spec-vars {:x "Integer"}
                                  :constraints [["xc" (> x 100)]]
                                  :refines-to {}}

                           :ws/Y {:spec-vars {:y "Integer"}
                                  :constraints []
                                  :refines-to {:ws/X {:expr {:$type :ws/X
                                                             :x y}}}}

                           :ws/Q {:spec-vars {:q "Integer"}
                                  :constraints []
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
           (hp/propagate '{:ws/A {:abstract? true
                                  :spec-vars {:a "Integer"}
                                  :constraints [["ac" (< a 200)]]
                                  :refines-to {}}

                           :ws/B {:spec-vars {:b "Integer"}
                                  :constraints []
                                  :refines-to {:ws/A {:expr {:$type :ws/A
                                                             :a b}}}}

                           :ws/X {:abstract? true
                                  :spec-vars {:x "Integer"}
                                  :constraints [["xc" (> x 100)]]
                                  :refines-to {}}

                           :ws/Y {:spec-vars {:y "Integer"}
                                  :constraints []
                                  :refines-to {:ws/X {:expr {:$type :ws/X
                                                             :x y}}}}

                           :ws/Q {:spec-vars {:q "Integer"}
                                  :constraints []
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
           (hp/propagate '{:ws/A {:abstract? true
                                  :spec-vars {:a "Integer"}
                                  :constraints [["ac" (< a 200)]]
                                  :refines-to {}}

                           :ws/B {:spec-vars {:b "Integer"}
                                  :constraints []
                                  :refines-to {:ws/A {:expr {:$type :ws/A
                                                             :a b}}}}

                           :ws/X {:abstract? true
                                  :spec-vars {:x "Integer"}
                                  :constraints [["xc" (> x 100)]]
                                  :refines-to {}}

                           :ws/Y {:spec-vars {:y "Integer"}
                                  :constraints []
                                  :refines-to {:ws/X {:expr {:$type :ws/X
                                                             :x y}}}}

                           :ws/Q {:spec-vars {:q "Integer"
                                              :p [:Maybe :ws/P]}
                                  :constraints []
                                  :refines-to {:ws/Y {:expr {:$type :ws/Y
                                                             :y (if-value p
                                                                          200
                                                                          q)}}
                                               :ws/B {:expr {:$type :ws/B
                                                             :b q}}}}
                           :ws/P {:spec-vars {}
                                  :constraints []
                                  :refines-to {}}}

                         {:$refines-to {:ws/A {:a {:$in [120 130]}}
                                        :ws/X {:x {:$in [120 130]}}}}))))

(deftest test-top-level-refines-to-bound-multiple-no-match
  #_(is (= :contradiction
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

  #_(is (thrown-with-msg? ExceptionInfo #"No concrete specs"
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

(deftest test-with-maybe-type
  #_(is (= {:$type :ws/B
            :$refines-to {:ws/A {:x {:$type [:Maybe :ws/X]
                                     :y {:$in [-1000 1000]}}}}})
        (hp/propagate '{:ws/A {:abstract? true
                               :spec-vars {:x [:Maybe :ws/X]}
                               :constraints []
                               :refines-to {}}

                        :ws/B {:spec-vars {:b "Integer"}
                               :constraints []
                               :refines-to {:ws/A {:expr {:$type :ws/A
                                                          :x (when (> b 0)
                                                               {:$type :ws/A
                                                                :y b})}}}}

                        :ws/P {:abstract? true
                               :spec-vars {}
                               :constraints []
                               :refines-to {:ws/A {:expr {:$type :ws/A}}}}

                        :ws/X {:spec-vars {:y "Integer"}
                               :constraints []
                               :refines-to {}}}

                      {:$refines-to {:ws/A {}
                                     :ws/P {}}})))

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

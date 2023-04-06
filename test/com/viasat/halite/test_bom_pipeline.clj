;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-bom-pipeline
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom-choco :as bom-choco]
            [com.viasat.halite.op-add-constraints :as op-add-constraints]
            [com.viasat.halite.op-add-types :as op-add-types]
            [com.viasat.halite.op-add-value-fields :as op-add-value-fields]
            [com.viasat.halite.op-canon :as op-canon]
            [com.viasat.halite.op-canon-refinements :as op-canon-refinements]
            [com.viasat.halite.op-canon-up :as op-canon-up]
            [com.viasat.halite.op-contradictions :as op-contradictions]
            [com.viasat.halite.op-ensure-fields :as op-ensure-fields]
            [com.viasat.halite.op-find-concrete :as op-find-concrete]
            [com.viasat.halite.op-inflate :as op-inflate]
            [com.viasat.halite.op-lower-expressions :as op-lower-expressions]
            [com.viasat.halite.op-mandatory :as op-mandatory]
            [com.viasat.halite.op-make-var-refs :as op-make-var-refs]
            [com.viasat.halite.op-merge-spec-bom :as op-merge-spec-bom]
            [com.viasat.halite.op-push-down-to-concrete :as op-push-down-to-concrete]
            [com.viasat.halite.op-remove-value-fields :as op-remove-value-fields]
            [com.viasat.halite.op-strip :as op-strip]
            [com.viasat.halite.op-syntax-check :as op-syntax-check]
            [com.viasat.halite.op-type-check :as op-type-check]
            [schema.test])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(defn pipeline [spec-env bom]
  (->> bom
       (op-syntax-check/syntax-check-op spec-env)
       (op-type-check/type-check-op spec-env)
       op-canon/canon-op
       (op-mandatory/mandatory-op spec-env)

       (op-find-concrete/find-concrete-op spec-env)
       op-push-down-to-concrete/push-down-to-concrete-op
       (op-canon-refinements/canon-refinements-op spec-env)

       op-contradictions/bubble-up-contradictions))

(defn pipeline-2 [spec-env bom]
  (->> bom
       (op-syntax-check/syntax-check-op spec-env)
       (op-type-check/type-check-op spec-env)
       op-canon/canon-op
       (op-mandatory/mandatory-op spec-env)

       (op-find-concrete/find-concrete-op spec-env)
       op-push-down-to-concrete/push-down-to-concrete-op
       (op-canon-refinements/canon-refinements-op spec-env)

       (op-merge-spec-bom/merge-spec-bom-op spec-env)
       (op-find-concrete/find-concrete-op spec-env)
       op-push-down-to-concrete/push-down-to-concrete-op
       (op-canon-refinements/canon-refinements-op spec-env)
       op-contradictions/bubble-up-contradictions
       (op-add-types/add-types-op spec-env)))

(deftest test-pipeline
  (is (= {:$instance-of :ws/A$v1
          :i 1
          :x {:$refines-to :ws/X$v1
              :$concrete-choices {:ws/Y$v1 {:$instance-of :ws/Y$v1
                                            :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                     :x2 2}}}
                                  :ws/ZZ$v1 {:$instance-of :ws/ZZ$v1
                                             :$refinements {:ws/Z$v1 {:$instance-of :ws/Z$v1
                                                                      :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                               :x2 2}}}}}}}}
         (pipeline {:ws/A$v1 {:fields {:i :Integer
                                       :x [:Maybe [:Instance :* #{:ws/X$v1}]]}}
                    :ws/X$v1 {:fields {:x2 :Integer}}
                    :ws/Y$v1 {:refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/Z$v1 {:abstract? true
                              :refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/ZZ$v1 {:refines-to {:ws/Z$v1 {:expr nil}}}}
                   {:$instance-of :ws/A$v1
                    :i 1
                    :x {:$refines-to :ws/X$v1
                        :x2 2}})))

  (is (= {:$instance-of :ws/A$v1
          :i 1
          :x {:$refines-to :ws/X$v1
              :$value? true
              :$concrete-choices
              {:ws/Y$v1 {:$instance-of :ws/Y$v1
                         :$refinements {:ws/X$v1
                                        {:$instance-of :ws/X$v1
                                         :x2 2
                                         :$value? true}}}
               :ws/ZZ$v1 {:$instance-of :ws/ZZ$v1
                          :$refinements {:ws/Z$v1 {:$instance-of :ws/Z$v1
                                                   :$value? true
                                                   :$refinements {:ws/X$v1
                                                                  {:$instance-of :ws/X$v1
                                                                   :x2 2
                                                                   :$value? true}}}}}
               :ws/ZZZ$v1 {:$instance-of :ws/ZZZ$v1
                           :$refinements {:ws/ZZ$v1 {:$instance-of :ws/ZZ$v1
                                                     :$value? true
                                                     :$refinements
                                                     {:ws/Z$v1 {:$instance-of :ws/Z$v1
                                                                :$value? true
                                                                :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                         :x2 2
                                                                                         :$value? true}}}}}}}}}}
         (pipeline {:ws/A$v1 {:fields {:i :Integer
                                       :x [:Instance :* #{:ws/X$v1}]}}
                    :ws/X$v1 {:fields {:x2 :Integer}}
                    :ws/Y$v1 {:refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/Z$v1 {:abstract? true
                              :refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/ZZ$v1 {:refines-to {:ws/Z$v1 {:expr nil}}}
                    :ws/ZZZ$v1 {:refines-to {:ws/ZZ$v1 {:expr nil}}}}
                   {:$instance-of :ws/A$v1
                    :i 1
                    :x {:$refines-to :ws/X$v1
                        :x2 2}})))

  (is (= {:$instance-of :ws/A$v1
          :i 1
          :x {:$value? false}}
         (pipeline {:ws/A$v1 {:fields {:i :Integer
                                       :x [:Maybe [:Instance :* #{:ws/X$v1}]]}}
                    :ws/X$v1 {:fields {:x2 :Integer}}
                    :ws/Y$v1 {:refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/Z$v1 {:abstract? true
                              :refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/ZZ$v1 {:refines-to {:ws/Z$v1 {:expr nil}}}}
                   {:$instance-of :ws/A$v1
                    :i 1
                    :x {:$refines-to :ws/X$v1
                        :$value? false}})))

  (is (= {:$instance-of :ws/A$v1
          :i {:$primitive-type :Integer
              :$value? true}
          :x {:$refines-to :ws/X$v1
              :$concrete-choices {:ws/Y$v1 {:$instance-of :ws/Y$v1
                                            :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1}}}
                                  :ws/ZZ$v1 {:$instance-of :ws/ZZ$v1
                                             :$refinements {:ws/Z$v1 {:$instance-of :ws/Z$v1
                                                                      :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1}}}}}}}}
         (pipeline-2 {:ws/A$v1 {:fields {:i :Integer
                                         :x [:Maybe [:Instance :* #{:ws/X$v1}]]}}
                      :ws/X$v1 {:fields {:x2 :Integer}}
                      :ws/Y$v1 {:refines-to {:ws/X$v1 {:expr nil}}}
                      :ws/Z$v1 {:abstract? true
                                :refines-to {:ws/X$v1 {:expr nil}}}
                      :ws/ZZ$v1 {:refines-to {:ws/Z$v1 {:expr nil}}}}
                     {:$instance-of :ws/A$v1})))

  (is (= {:$instance-of :ws/A$v1
          :i {:$primitive-type :Integer
              :$value? true}
          :x {:$instance-of :ws/Y$v1
              :x3 {:$primitive-type :Integer
                   :$value? true
                   :$ranges #{[1 10000]}}
              :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1}}}}
         (pipeline-2 {:ws/A$v1 {:fields {:i :Integer
                                         :x [:Maybe [:Instance :* #{:ws/X$v1}]]}}
                      :ws/X$v1 {:fields {:x2 :Integer}}
                      :ws/Y$v1 {:fields {:x3 :Integer}
                                :constraints [["x3" '(and (> x3 0) (< x3 10000))]]
                                :refines-to {:ws/X$v1 {:expr nil}}}
                      :ws/Z$v1 {:abstract? true
                                :refines-to {:ws/X$v1 {:expr nil}}}
                      :ws/ZZ$v1 {:refines-to {:ws/Z$v1 {:expr nil}}}}
                     {:$instance-of :ws/A$v1
                      :x {:$instance-of :ws/Y$v1}}))))

(defmacro check-propagate [spec-env bom expected]
  `(let [spec-env# ~spec-env
         bom# (->> ~bom
                   (op-syntax-check/syntax-check-op spec-env#)
                   (op-type-check/type-check-op spec-env#)
                   op-canon/canon-op
                   (op-mandatory/mandatory-op spec-env#)

                   (op-find-concrete/find-concrete-op spec-env#)
                   op-push-down-to-concrete/push-down-to-concrete-op
                   (op-canon-refinements/canon-refinements-op spec-env#)

                   (op-merge-spec-bom/merge-spec-bom-op spec-env#)
                   (op-find-concrete/find-concrete-op spec-env#)
                   op-push-down-to-concrete/push-down-to-concrete-op
                   (op-canon-refinements/canon-refinements-op spec-env#)
                   op-contradictions/bubble-up-contradictions
                   (op-add-types/add-types-op spec-env#)
                   (op-ensure-fields/ensure-fields-op spec-env#)
                   (op-add-value-fields/add-value-fields-op spec-env#)
                   (op-add-constraints/add-constraints-op spec-env#)
                   op-make-var-refs/make-var-refs-op
                   op-lower-expressions/lower-expression-op)
         result# (->> bom#
                      bom-choco/bom-to-choco
                      (bom-choco/paths-to-syms bom#)
                      (bom-choco/choco-propagate bom#)
                      bom-choco/propagate-results-to-bounds
                      (op-inflate/inflate-op (op-remove-value-fields/remove-value-fields-op spec-env# bom#))
                      (op-remove-value-fields/remove-value-fields-op spec-env#)
                      op-canon-up/canon-up-op
                      op-strip/strip-op)
         expected# ~expected]
     (is (= expected# result#))
     (when-not (= expected# result#)
       result#)))

(deftest test-propagate
  (check-propagate {:ws/A$v1 {:fields {:x :Integer
                                       :y :Integer}
                              :constraints [["c1" '(> x y)]]}}
                   {:$instance-of :ws/A$v1}
                   {:$instance-of :ws/A$v1
                    :x {:$ranges #{[-999 1000]}}
                    :y {:$ranges #{[-1000 999]}}})

  ;; test constraint across two fields
  (check-propagate {:ws/A$v1 {:fields {:x :Integer
                                       :y :Integer}
                              :constraints [["c1" '(= 15 (+ x y))]
                                            ["c2" '(or (= x 1)
                                                       (= x 2)
                                                       (= x 3))]
                                            ["c3" '(or (= y 10)
                                                       (= y 12)
                                                       (= y 14))]]}}
                   {:$instance-of :ws/A$v1}
                   {:$instance-of :ws/A$v1
                    :x {:$enum #{1 3}}
                    :y {:$enum #{12 14}}})

  ;; test composition
  (check-propagate {:ws/B$v1 {:fields {:a [:Instance :ws/A$v1]
                                       :y :Integer}
                              :constraints [["c1" '(= 15 (+ (get a :x) y))]
                                            ["c3" '(or (= y 10)
                                                       (= y 12)
                                                       (= y 14))]]}
                    :ws/A$v1 {:fields {:x :Integer}
                              :constraints [["c2" '(or (= x 1)
                                                       (= x 2)
                                                       (= x 3))]]}}
                   {:$instance-of :ws/B$v1}
                   {:$instance-of :ws/B$v1
                    :a {:$instance-of :ws/A$v1
                        :x {:$enum #{1 3}}}
                    :y {:$enum #{12 14}}})

  ;; test optional-field
  (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                   {:$instance-of :ws/A$v1}
                   {:$instance-of :ws/A$v1
                    :x {:$ranges #{[-1000 1000]}}})

  (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Integer]}
                              :constraints [["c1" '(if-value x (and (> x 20) (< x 30)) true)]]}}
                   {:$instance-of :ws/A$v1}
                   {:$instance-of :ws/A$v1
                    :x {:$enum #{27 24 21 22 29 28 25 23 26}}})

  (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Integer]}
                              :constraints [["c1" '(if-value x false true)]]}}
                   {:$instance-of :ws/A$v1}
                   {:$instance-of :ws/A$v1
                    :x {:$value? false}})

  (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Integer]}
                              :constraints [["c1" '(if-value x true false)]]}}
                   {:$instance-of :ws/A$v1}
                   {:$instance-of :ws/A$v1
                    :x {:$value? true
                        :$ranges #{[-1000 1000]}}})

  (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Integer]
                                       :y [:Maybe :Integer]}
                              :constraints [["c1" '(if-value x (if-value y true false) false)]]}}
                   {:$instance-of :ws/A$v1}
                   {:$instance-of :ws/A$v1
                    :x {:$value? true
                        :$ranges #{[-1000 1000]}}
                    :y {:$value? true
                        :$ranges #{[-1000 1000]}}})

  (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Integer]
                                       :y [:Maybe :Integer]}
                              :constraints [["c1" '(if-value x false (if-value y true false))]]}}
                   {:$instance-of :ws/A$v1}
                   {:$instance-of :ws/A$v1
                    :x {:$value? false}
                    :y {:$value? true
                        :$ranges #{[-1000 1000]}}})

  (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Integer]
                                       :y [:Maybe :Integer]}
                              :constraints [["c1" '(if-value x false (if-value y false true))]]}}
                   {:$instance-of :ws/A$v1}
                   {:$instance-of :ws/A$v1
                    :x {:$value? false}
                    :y {:$value? false}})

  ;; optional-field with composition
  (check-propagate {:ws/B$v1 {:fields {:a [:Instance :ws/A$v1]
                                       :y [:Maybe :Integer]}
                              :constraints [["c1" '(if-value y
                                                             (= 15 (+ (get a :x) y))
                                                             true)]
                                            ["c3" '(if-value y
                                                             (or (= y 10)
                                                                 (= y 12)
                                                                 (= y 14))
                                                             true)]]}
                    :ws/A$v1 {:fields {:x :Integer}
                              :constraints [["c2" '(or (= x 1)
                                                       (= x 2)
                                                       (= x 3))]]}}
                   {:$instance-of :ws/B$v1}
                   {:$instance-of :ws/B$v1
                    :a {:$instance-of :ws/A$v1
                        :x {:$enum #{1 3 2}}}
                    :y {:$enum #{12 14 10}}})

  (check-propagate {:ws/B$v1 {:fields {:a [:Instance :ws/A$v1]
                                       :y [:Maybe :Integer]}
                              :constraints [["c1" '(if-value y
                                                             (= 15 (+ (get a :x) y))
                                                             false)]
                                            ["c3" '(if-value y
                                                             (or (= y 10)
                                                                 (= y 12)
                                                                 (= y 14))
                                                             true)]]}
                    :ws/A$v1 {:fields {:x :Integer}
                              :constraints [["c2" '(or (= x 1)
                                                       (= x 2)
                                                       (= x 3))]]}}
                   {:$instance-of :ws/B$v1}
                   {:$instance-of :ws/B$v1
                    :a {:$instance-of :ws/A$v1
                        :x {:$enum #{1 3}}}
                    :y {:$value? true
                        :$enum #{12 14}}})

  (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]}}
                    :ws/A$v1 {:fields {:x :Integer}}}
                   {:$instance-of :ws/B$v1}
                   {:$instance-of :ws/B$v1
                    :a {:$instance-of :ws/A$v1
                        :x {:$ranges #{[-1000 1000]}}}})

  (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]}
                              :constraints [["c1" '(if-value a
                                                             false
                                                             true)]]}
                    :ws/A$v1 {:fields {}}}
                   {:$instance-of :ws/B$v1}
                   {:$instance-of :ws/B$v1
                    :a {:$value? false}})

  (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]}
                              :constraints [["c1" '(if-value a
                                                             true
                                                             false)]]}
                    :ws/A$v1 {:fields {}}}
                   {:$instance-of :ws/B$v1}
                   {:$instance-of :ws/B$v1
                    :a {:$instance-of :ws/A$v1
                        :$value? true}})

  (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]}
                              :constraints [["c1" '(if-value a
                                                             (let [q (get a :x)]
                                                               (if-value q
                                                                         false
                                                                         true))
                                                             false)]]}
                    :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                   {:$instance-of :ws/B$v1}
                   {:$instance-of :ws/B$v1
                    :a {:$instance-of :ws/A$v1
                        :$value? true
                        :x {:$value? false}}})

  (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]}
                              :constraints [["c1" '(if-value a
                                                             (let [q (get a :x)]
                                                               (if-value q
                                                                         true
                                                                         false))
                                                             false)]]}
                    :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                   {:$instance-of :ws/B$v1}
                   {:$instance-of :ws/B$v1
                    :a {:$instance-of :ws/A$v1
                        :$value? true
                        :x {:$value? true
                            :$ranges #{[-1000 1000]}}}})

  (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]
                                       :b :Boolean}
                              :constraints [["c1" '(if b
                                                     true
                                                     (if-value a
                                                               (let [q (get a :x)]
                                                                 (if-value q
                                                                           true
                                                                           false))
                                                               false))]]}
                    :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                   {:$instance-of :ws/B$v1
                    :b false}
                   {:$instance-of :ws/B$v1
                    :b false
                    :a {:$instance-of :ws/A$v1
                        :$value? true
                        :x {:$value? true
                            :$ranges #{[-1000 1000]}}}})

  (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]
                                       :b :Boolean}
                              :constraints [["c1" '(if b
                                                     true
                                                     (if-value a
                                                               (let [q (get a :x)]
                                                                 (if-value q
                                                                           (> q 20)
                                                                           false))
                                                               false))]]}
                    :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                   {:$instance-of :ws/B$v1
                    :b false}
                   {:$instance-of :ws/B$v1
                    :b false
                    :a {:$instance-of :ws/A$v1
                        :$value? true
                        :x {:$value? true
                            :$ranges #{[21 1000]}}}})

  (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]
                                       :b :Boolean}
                              :constraints [["c1" '(if b
                                                     true
                                                     (if-value a
                                                               (let [q (get a :x)]
                                                                 (if-value q
                                                                           (> q 20)
                                                                           false))
                                                               false))]]}
                    :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                   {:$instance-of :ws/B$v1
                    :b false}
                   {:$instance-of :ws/B$v1
                    :b false
                    :a {:$instance-of :ws/A$v1
                        :$value? true
                        :x {:$value? true
                            :$ranges #{[21 1000]}}}})
  (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]
                                       :b :Boolean}
                              :constraints [["c1" '(if b
                                                     true
                                                     (if-value a
                                                               (let [q (get a :x)]
                                                                 (if-value q
                                                                           (> q 20)
                                                                           false))
                                                               false))]]}
                    :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                   {:$instance-of :ws/B$v1
                    :a {:$instance-of :ws/A$v1
                        :x 30}}
                   {:$instance-of :ws/B$v1
                    :a {:$instance-of :ws/A$v1
                        :x 30}})
  (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]
                                       :b :Boolean}
                              :constraints [["c1" '(if b
                                                     true
                                                     (if-value a
                                                               (let [q (get a :x)]
                                                                 (if-value q
                                                                           (> q 20)
                                                                           false))
                                                               false))]]}
                    :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                   {:$instance-of :ws/B$v1
                    :a {:$instance-of :ws/A$v1
                        :x 10}}
                   {:$instance-of :ws/B$v1
                    :a {:$instance-of :ws/A$v1
                        :x 10}
                    :b true}))

;; (set! *print-namespace-maps* false)

;; (time (run-tests))

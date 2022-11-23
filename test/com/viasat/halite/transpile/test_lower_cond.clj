;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.transpile.test-lower-cond
  (:require [com.viasat.halite.transpile.lower-cond :as lower-cond]
            [schema.test :refer [validate-schemas]])
  (:use clojure.test))

(deftest test-lower-cond
  (is (= '(+ 1 2)
         (#'lower-cond/lower-cond-in-expr '(+ 1 2))))
  (is (= '(if x y)
         (#'lower-cond/lower-cond-in-expr '(if x y))))
  (is (= '(if a b (if x y z))
         (#'lower-cond/lower-cond-in-expr '(cond a b x y z))))
  (is (= '(if a b (if x y (if p q r)))
         (#'lower-cond/lower-cond-in-expr '(cond a b x y p q r))))
  (is (= '(when true (if a b (if x y (if p q r))))
         (#'lower-cond/lower-cond-in-expr '(when true (cond a b x y p q r)))))
  (is (= '[(if a b (if x y z))]
         (#'lower-cond/lower-cond-in-expr '[(cond a b x y z)])))
  (is (= '#{(if a b (if x y z))
            (+ 1 2)
            (if p q r)}
         (#'lower-cond/lower-cond-in-expr '#{(cond a b x y z)
                                             (+ 1 2)
                                             (cond p q r)})))
  (is (= {:name "c"
          :expr '(if a b (if x y z))}
         (#'lower-cond/lower-cond-in-expr-object {:name "c"
                                                  :expr '(cond a b x y z)})))
  (is (= {:expr '(if a b (if x y z))}
         (#'lower-cond/lower-cond-in-expr-object {:expr '(cond a b x y z)})))
  (is (= {:inverted? true
          :expr '(if a b (if x y z))}
         (#'lower-cond/lower-cond-in-expr-object {:inverted? true
                                                  :expr '(cond a b x y z)})))
  (is (= {:inverted? true
          :name "c"
          :expr '(if a b (if x y z))}
         (#'lower-cond/lower-cond-in-expr-object {:inverted? true
                                                  :name "c"
                                                  :expr '(cond a b x y z)})))

  (let [spec-1-cond {:$type :spec/A
                     :spec-vars {:x :Integer}
                     :abstract? false
                     :constraints [["c1" '(cond a b x y z)]
                                   ["c2" '(if p q r)]]
                     :refines-to {:spec/B {:name "r1"
                                           :expr '(cond a b x y z p q r)}}}
        spec-1-if {:$type :spec/A
                   :spec-vars {:x :Integer}
                   :abstract? false
                   :constraints [["c1" '(if a b (if x y z))]
                                 ["c2" '(if p q r)]]
                   :refines-to {:spec/B {:name "r1"
                                         :expr '(if a b (if x y (if z p (if q r r))))}}}]
    (is (= spec-1-if
           (#'lower-cond/lower-cond-in-spec spec-1-cond)))

    (is (= {:spec/A spec-1-if}
           (lower-cond/lower-cond-in-spec-map {:spec/A spec-1-cond})))))

;; (run-tests)

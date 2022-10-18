;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-choco-clj-opt
  (:require [schema.test]
            [com.viasat.halite.choco-clj-opt :as cco])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

(def lower-expr #'cco/lower-expr)

(deftest test-lower-expr
  (let [witness-map '{x x?}]
    (are [expr lowered]
         (= lowered (try (lower-expr witness-map #{'y} expr)
                         (catch Exception ex
                           :error)))

      1 1
      true true
      'y 'y
      '(+ y 1) '(+ y 1)
      'x :error
      '(if-value x (+ x 1) 0) '(if x? (+ x 1) 0)
      '(if-value y 1 2) :error
      '(let [z (+ y y)] (* z 2)) '(let [z (+ y y)] (* z 2))
      '(let [z (if p x y)] z) :error
      '(let [y x] y) :error)))

(def lower-spec #'cco/lower-spec)
(def lower-bounds #'cco/lower-bounds)

(deftest test-single-optional-var
  (let [spec '{:vars {x :Int, y :Int, p :Bool}
               :optionals #{x}
               :constraints
               #{(< (if-value x x 1) y)
                 (< y 10)
                 (if-value x (not p) true)}}]

    (binding [cco/*default-int-bounds* [-20 20]]
      (is (= '[{:vars {x :Int, x? :Bool, y :Int, p :Bool}
                :constraints
                #{(< (if x? x 1) y)
                  (< y 10)
                  (if x? (not p) true)}}
               {x x?}]
             (lower-spec spec)))

      (are [bounds vars]
           (= vars (->> bounds (lower-bounds '{x x?})))

        '{x :Unset}               '{x? false}
        '{x 1}                    '{x 1, x? true}
        '{x #{1 2 :Unset}}        '{x #{1 2}}
        '{x #{1 2}}               '{x #{1 2}, x? true}
        '{x [1 10 :Unset]}        '{x [1 10]}
        '{x [1 10]}               '{x [1 10], x? true})

      (are [in out]
           (= out (cco/propagate spec in))

        '{}             '{x [-20 8 :Unset], y [-19 9], p #{true false}}
        '{x :Unset}     '{x :Unset,         y [2 9],   p #{true false}}
        '{p true}       '{x :Unset,         y [2 9],   p true}
        '{y -3}         '{x [-20 -4],       y -3,      p false}))))

(deftest test-multiple-optional-vars
  (let [spec '{:vars {x [0 100], y [0 100], z [0 100]}
               :optionals #{y z}
               :constraints
               #{(if-value y
                           (and (< x y)
                                (if-value z true false))
                           true)
                 (if-value z
                           (< z (* x 2))
                           true)}}]

    (are [in out]
         (= out (cco/propagate spec in))

      '{} '{x [0 100], y [2 100 :Unset], z [0 100 :Unset]}
      '{x 12} '{x 12, y [13 100 :Unset], z [0 23 :Unset]}
      '{z 10} '{x [6 100], y [7 100 :Unset], z 10}
      '{y 10} '{x [1 9], y 10, z [0 17]})))

(deftest test-optional-that-must-not-be-set
  (let [spec '{:vars {w [0 10], p :Bool}
               :optionals #{w}
               :constraints #{(=> p (if-value w true false))
                              (=> (not p) (if-value w false true))
                              (=> p (if-value w (< w 0) true))}}]
    (is (= '{w :Unset, p false} (cco/propagate spec)))))

(deftest test-optional-var-with-empty-initial-bound
  (is (thrown? clojure.lang.ExceptionInfo
               (cco/propagate '{:vars {x :Int} :optionals #{x} :constraints #{}}
                              '{x #{}}))))

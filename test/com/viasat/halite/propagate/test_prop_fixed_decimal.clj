;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-fixed-decimal
  (:require [com.viasat.halite.propagate.prop-fixed-decimal :as prop-fixed-decimal]
            [com.viasat.halite.propagate.prop-composition :as prop-composition]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite :as halite]
            [com.viasat.halite.var-types :as var-types]
            [schema.test :refer [validate-schemas]])
  (:use clojure.test))

(deftest test-lower-fixed-decimal
  (let [spec-1 {:$type :spec/A
                :fields {:x [:Decimal 2]}
                :abstract? false
                :constraints [["c1" '(= x #d "1.23")]]
                :refines-to {:spec/B {:name "r1"
                                      :expr '#d "9.72"}}}]
    (is (= {:$type :spec/A
            :fields {:x :Integer}
            :abstract? false
            :constraints [["$all" '(= x 123)]]
            :refines-to {:spec/B {:name "r1"
                                  :expr '972}}}
           (->> {:spec/A spec-1}
                (#'prop-fixed-decimal/lowered-spec-context)
                :spec/A
                ssa/spec-from-ssa)))))

(deftest test-walk-bound
  (is (= 1
         (#'prop-fixed-decimal/walk-bound {} 1)))
  (is (= {:$type :spec/A
          :x 100}
         (#'prop-fixed-decimal/walk-bound {} {:$type :spec/A
                                              :x 100})))
  (is (= {:$type :spec/A
          :x {:$type :spec/X
              :z 100}}
         (#'prop-fixed-decimal/walk-bound {} {:$type :spec/A
                                              :x {:$type :spec/X
                                                  :z 100}})))
  (is (= {:$type :spec/A
          :x {:$type :spec/X
              :z 100}
          :$refines-to {:ws/Painted {:color {:$in #{"red" "yellow"}}
                                     :w {:$type :ws/Wheel
                                         :$refines-to {:ws/Round {:radius 20}}
                                         :r 109}}}}
         (#'prop-fixed-decimal/walk-bound {:f #'prop-fixed-decimal/fixed-decimal-to-long}
                                          {:$type :spec/A
                                           :x {:$type :spec/X
                                               :z 100}
                                           :$refines-to {:ws/Painted {:color {:$in #{"red" "yellow"}}
                                                                      :w {:$type :ws/Wheel
                                                                          :$refines-to {:ws/Round {:radius #d "0.20"}}
                                                                          :r #d "0.109"}}}})))

  (is (= {:$type :ws/Car :$refines-to {:ws/Painted {:color {:$in #{"red" "yellow"}}
                                                    :w {:$type :ws/Wheel
                                                        :$refines-to {:ws/Round {:radius 20}}
                                                        :r 10}}}}
         (#'prop-fixed-decimal/walk-bound {} {:$type :ws/Car :$refines-to {:ws/Painted {:color {:$in #{"red" "yellow"}}
                                                                                        :w {:$type :ws/Wheel
                                                                                            :$refines-to {:ws/Round {:radius 20}}
                                                                                            :r 10}}}})))
  (is (= {:$in {:ws/A {:$refines-to {}}
                :ws/B {:$refines-to {:ws/A {}}}}
          :$refines-to {}}
         (#'prop-fixed-decimal/walk-bound {} {:$in {:ws/A {:$refines-to {}}
                                                    :ws/B {:$refines-to {:ws/A {}}}}
                                              :$refines-to {}})))
  (is (= {:$in {:ws/A {:$refines-to {:ws/A {:x 14}}}
                :ws/B {:$refines-to {:ws/A {:r 20}}}}
          :$refines-to {}}
         (#'prop-fixed-decimal/walk-bound {:g (fn [context i]
                                                (let [{:keys [field type]} context]
                                                  (if (and (= field :r)
                                                           (= type :ws/A))
                                                    (inc i)
                                                    i)))}
                                          {:$in {:ws/A {:$refines-to {:ws/A {:x 14}}}
                                                 :ws/B {:$refines-to {:ws/A {:r 19}}}}
                                           :$refines-to {}})))

  (is (= {:$type :ws/Z,
          :z {:$in {:ws/A {:$refines-to {:ws/A {:x 14}}}
                    :ws/B {:$refines-to {:ws/A {:r 19}}}}}}
         (#'prop-fixed-decimal/walk-bound {}
                                          {:$type :ws/Z
                                           :z {:$in {:ws/A {:$refines-to {:ws/A {:x 14}}}
                                                     :ws/B {:$refines-to {:ws/A {:r 19}}}}}}))))

(deftest test-propagate
  (let [vspecs '{:my/A {:fields {:x [:Decimal 2]
                                 :y [:Decimal 1]}
                        :constraints #{{:name "fancy"
                                        :expr (< #d "3.0" (+ y (* (rescale x 1) 10)))}}}}
        specs (var-types/to-halite-spec-env vspecs) ;; for prop-fixed-decimal
        sctx (ssa/spec-map-to-ssa specs)] ;; for lower layers of propagation

    (is (= {:$type :my/A, :x #d "0.23", :y #d "1.3"}
           (halite/eval-expr vspecs (halite/type-env {}) (halite/env {})
                             '{:$type :my/A, :x #d "0.23", :y #d "1.3"})))

    (is (= {:$type :my/A,
            :x {:$in [#d "-10.00" #d "10.00"]}
            :y {:$in [#d "-96.9" #d "100.0"]}}
           (prop-fixed-decimal/propagate specs
                                         prop-composition/default-options
                                         {:$type :my/A})))))

;; (run-tests)

;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-fixed-decimal
  (:require [com.viasat.halite.propagate.prop-fixed-decimal :as prop-fixed-decimal]
            [schema.test :refer [validate-schemas]])
  (:use clojure.test))

(deftest test-lower-fixed-decimal
  (let [spec-1 {:$type :spec/A
                :spec-vars {:x [:Decimal 2]}
                :abstract? false
                :constraints [["c1" '(= x #d "1.23")]]
                :refines-to {:spec/B {:name "r1"
                                      :expr '#d "9.72"}}}]
    (is (= {:spec/A {:$type :spec/A
                     :spec-vars {:x :Integer}
                     :abstract? false
                     :constraints [["c1" '(= x 123)]]
                     :refines-to {:spec/B {:name "r1"
                                           :expr '972}}}}
           (prop-fixed-decimal/encode-fixed-decimals-in-spec-map {:spec/A spec-1})))))

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
                                                                                            :r 10}}}}))))

;; (run-tests)

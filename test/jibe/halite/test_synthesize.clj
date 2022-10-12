;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.test-synthesize
  (:require
   [jibe.halite.synthesize :refer [synthesize] :as synth]
   [clojure.test :as t :refer [deftest is]]))

(deftest thing1
  (let [exprs-data
        (synthesize
         {:spec/A {}
          :spec/B {:refines-to {:spec/A {:name "as_A"
                                         :expr '{:$type :spec/A}}}}})]

    (is (= {:spec/A {:predicate '(fn [$exprs $this]
                                   (and (map? $this)
                                        (= :spec/A (:$type $this))
                                        (= #{:$type} (set (keys $this)))))
                     :refines-to {}}
            :spec/B {:predicate '(fn [$exprs $this]
                                   (and (map? $this)
                                        (= :spec/B (:$type $this))
                                        (= #{:$type} (set (keys $this)))
                                        ;; non-inverted refinement
                                        (if-let [refined ((get-in $exprs [:spec/B :refines-to :spec/A]) $exprs $this)]
                                          ((get-in $exprs [:spec/A :predicate]) $exprs refined)
                                          true)))
                     :refines-to {:spec/A '(fn [$exprs {:keys []}]
                                             {:$type :spec/A})}}}
           exprs-data))

    (is (= {:$type :spec/B}
           (synth/eval exprs-data '{:$type :spec/B})))

    (is (= {:$type :spec/A}
           (synth/eval exprs-data '(refine-to {:$type :spec/B} :spec/A))))

    (is (= true
           (synth/eval exprs-data '(refines-to? {:$type :spec/B} :spec/A))))

    (is (= {:$type :spec/B}
           (synth/eval exprs-data '(valid {:$type :spec/B}))))

    (is (= true
           (synth/eval exprs-data '(valid? {:$type :spec/B}))))))

(deftest test-transitive-refinements
  (let [exprs-data
        (synthesize
         {:spec/A {}
          :spec/B {:refines-to {:spec/A {:name "as_A"
                                         :expr '{:$type :spec/A}}}}
          :spec/C {:refines-to {:spec/B {:name "as_B"
                                         :expr '{:$type :spec/B}}}}})]

    (is (= {:spec/A {:predicate '(fn [$exprs $this]
                                   (and (map? $this)
                                        (= :spec/A (:$type $this))
                                        (= #{:$type} (set (keys $this)))))
                     :refines-to {}}
            :spec/B {:predicate '(fn [$exprs $this]
                                   (and (map? $this)
                                        (= :spec/B (:$type $this))
                                        (= #{:$type} (set (keys $this)))
                                        (if-let [refined ((get-in $exprs [:spec/B :refines-to :spec/A]) $exprs $this)]
                                          ((get-in $exprs [:spec/A :predicate]) $exprs refined)
                                          true)))
                     :refines-to {:spec/A '(fn [$exprs {:keys []}]
                                             {:$type :spec/A})}}
            :spec/C {:predicate '(fn [$exprs $this]
                                   (and (map? $this)
                                        (= :spec/C (:$type $this))
                                        (= #{:$type} (set (keys $this)))
                                        (if-let [refined ((get-in $exprs [:spec/C :refines-to :spec/B]) $exprs $this)]
                                          ((get-in $exprs [:spec/B :predicate]) $exprs refined)
                                          true)))
                     :refines-to {:spec/A '(fn [$exprs $this]
                                             ((get-in
                                               $exprs
                                               [:spec/B :refines-to :spec/A])
                                              $exprs
                                              ((get-in
                                                $exprs
                                                [:spec/C :refines-to :spec/B])
                                               $exprs
                                               $this)))
                                  :spec/B '(fn [$exprs {:keys []}]
                                             {:$type :spec/B})}}}
           exprs-data))
    (is (= {:$type :spec/A}
           (synth/eval exprs-data '(refine-to {:$type :spec/C} :spec/A))))
    (is (= true
           (synth/eval exprs-data '(refines-to? {:$type :spec/C} :spec/A))))))

(deftest test-constraints
  (is (= {:spec/A {:predicate '(fn [$exprs $this]
                                 (and
                                  (map? $this)
                                  (= :spec/A (:$type $this))
                                  (= #{:$type :x} (set (keys $this)))
                                  (let [{:keys [x]} $this]
                                    (and (> x 12)))))
                   :refines-to {}}}
         (synthesize {:spec/A {:spec-vars {:x "Integer"}
                               :constraints [["c" '(> x 12)]]}})))
  (is (= {:spec/A {:predicate '(fn [$exprs $this]
                                 (and
                                  (map? $this)
                                  (= :spec/A (:$type $this))
                                  (= #{:$type :x} (set (keys $this)))
                                  (let [{:keys [x]} $this]
                                    (and (> x 12)
                                         (< x 24)))))
                   :refines-to {}}}
         (synthesize {:spec/A {:spec-vars {:x "Integer"}
                               :constraints [["c" '(> x 12)]
                                             ["c2" '(< x 24)]]}}))))

(deftest test-optional-field
  (let [exprs-data (synthesize {:spec/A {:spec-vars {:x [:Maybe "Integer"]}}})]
    (is (= {:spec/A {:predicate '(fn [$exprs $this]
                                   (and
                                    (map? $this)
                                    (= :spec/A (:$type $this))
                                    (clojure.set/subset? (set (keys $this)) #{:$type :x})
                                    (clojure.set/subset? #{:$type} (set (keys $this)))))
                     :refines-to {}}}
           exprs-data))
    (is (= {:$type :spec/A}
           (synth/eval exprs-data '{:$type :spec/A})))
    (is (= {:$type :spec/A :x 1}
           (synth/eval exprs-data '{:$type :spec/A :x 1})))
    (is (nil?
         (synth/eval exprs-data '(valid {:$type :spec/A :x 1 :y 0})))))

  (let [exprs-data (synthesize {:spec/A {:spec-vars {:x [:Maybe "Integer"]
                                                     :y "Integer"}}})]
    (is (= {:spec/A {:predicate '(fn [$exprs $this]
                                   (and
                                    (map? $this)
                                    (= :spec/A (:$type $this))
                                    (clojure.set/subset? (set (keys $this)) #{:$type :x :y})
                                    (clojure.set/subset? #{:$type :y} (set (keys $this)))))
                     :refines-to {}}}
           exprs-data))
    (is (= {:$type :spec/A :y 0}
           (synth/eval exprs-data '{:$type :spec/A :y 0})))
    (is (= {:$type :spec/A :x 1 :y 0}
           (synth/eval exprs-data {:$type :spec/A :x 1 :y 0})))
    (is (nil?
         (synth/eval exprs-data '(valid {:$type :spec/A :x 1 :y 0 :z 2}))))))

;; (t/run-tests)

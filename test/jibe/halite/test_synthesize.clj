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

    (let [{:keys [validate-instance refine-to refines-to? valid valid?]}
          (synth/compile-exprs exprs-data)]

      (is (= {:$type :spec/B}
             (validate-instance {:$type :spec/B})))

      (is (= {:$type :spec/A}
             (refine-to {:$type :spec/B} :spec/A)))

      (is (= true
             (refines-to? {:$type :spec/B} :spec/A)))

      (is (= {:$type :spec/B}
             (valid {:$type :spec/B})))

      (is (= true
             (valid? {:$type :spec/B}))))))

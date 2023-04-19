;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom-pipeline
  (:require [clojure.pprint :as pprint]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-choco :as bom-choco]
            [com.viasat.halite.bom-user :as bom-user]
            [com.viasat.halite.op-add-constraints :as op-add-constraints]
            [com.viasat.halite.op-add-types :as op-add-types]
            [com.viasat.halite.op-add-value-fields :as op-add-value-fields]
            [com.viasat.halite.op-canon :as op-canon]
            [com.viasat.halite.op-canon-refinements :as op-canon-refinements]
            [com.viasat.halite.op-canon-up :as op-canon-up]
            [com.viasat.halite.op-conjoin-spec-bom :as op-conjoin-spec-bom]
            [com.viasat.halite.op-contradictions :as op-contradictions]
            [com.viasat.halite.op-ensure-fields :as op-ensure-fields]
            [com.viasat.halite.op-find-concrete :as op-find-concrete]
            [com.viasat.halite.op-flower :as op-flower]
            [com.viasat.halite.op-id :as op-id]
            [com.viasat.halite.op-inflate :as op-inflate]
            [com.viasat.halite.op-mandatory :as op-mandatory]
            [com.viasat.halite.op-push-down-to-concrete :as op-push-down-to-concrete]
            [com.viasat.halite.op-remove-value-fields :as op-remove-value-fields]
            [com.viasat.halite.op-strip :as op-strip]
            [com.viasat.halite.op-syntax-check :as op-syntax-check]
            [com.viasat.halite.op-type-check :as op-type-check]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn propagate :- {:expanded-bom bom/Bom
                      :lowered-bom bom/Bom
                      :choco-data s/Any
                      :propagate-result s/Any
                      :result bom-user/UserBom}
  [spec-env
   input-bom :- bom-user/UserBom]
  (let [expanded-bom (->> input-bom
                          (op-syntax-check/syntax-check-op spec-env)
                          (op-type-check/type-check-op spec-env)
                          op-canon/canon-op
                          (op-mandatory/mandatory-op spec-env)

                          (op-find-concrete/find-concrete-op spec-env)
                          op-push-down-to-concrete/push-down-to-concrete-op
                          (op-canon-refinements/canon-refinements-op spec-env)

                          (op-conjoin-spec-bom/conjoin-spec-bom-op spec-env)
                          (op-find-concrete/find-concrete-op spec-env)
                          op-push-down-to-concrete/push-down-to-concrete-op
                          (op-canon-refinements/canon-refinements-op spec-env)
                          op-contradictions/bubble-up-contradictions
                          (op-add-types/add-types-op spec-env)
                          (op-ensure-fields/ensure-fields-op spec-env)
                          (op-add-value-fields/add-value-fields-op spec-env)
                          (op-add-constraints/add-constraints-op spec-env))
        ;; _ (pprint/pprint [:expanded-bom expanded-bom])
        lowered-bom (->> expanded-bom
                         op-id/id-op
                         (op-flower/flower-op spec-env))
        ;; _ (pprint/pprint [:lowered-bom lowered-bom])
        choco-data (when-not (bom/is-contradiction-bom? lowered-bom)
                     (->> lowered-bom
                          bom-choco/bom-to-choco
                          (bom-choco/paths-to-syms lowered-bom)))
        propagate-result (when-not (bom/is-contradiction-bom? lowered-bom)
                           (->> choco-data
                                (bom-choco/choco-propagate expanded-bom)))
        result (if (bom/is-contradiction-bom? lowered-bom)
                 lowered-bom
                 (->> propagate-result
                      (bom-choco/propagate-results-to-bounds expanded-bom)
                      (op-inflate/inflate-op (op-remove-value-fields/remove-value-fields-op spec-env expanded-bom))
                      (op-remove-value-fields/remove-value-fields-op spec-env)
                      op-canon-up/canon-up-op
                      op-strip/strip-op))]
    {:expanded-bom expanded-bom
     :lowered-bom lowered-bom
     :choco-data choco-data
     :propagate-result propagate-result
     :result result}))

;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom-pipeline
  (:require [clojure.pprint :as pprint]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-choco :as bom-choco]
            [com.viasat.halite.bom-pipeline-base :as bom-pipeline-base]
            [com.viasat.halite.bom-user :as bom-user]
            [com.viasat.halite.op-canon-up :as op-canon-up]
            [com.viasat.halite.op-flower :as op-flower]
            [com.viasat.halite.op-id :as op-id]
            [com.viasat.halite.op-inflate :as op-inflate]
            [com.viasat.halite.op-remove-value-fields :as op-remove-value-fields]
            [com.viasat.halite.op-strip :as op-strip]
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
                          (bom-pipeline-base/check-input-bom spec-env)
                          (bom-pipeline-base/expand-bom spec-env))
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

;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom-pipeline-base
  (:require [clojure.pprint :as pprint]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-user :as bom-user]
            [com.viasat.halite.op-add-constraints :as op-add-constraints]
            [com.viasat.halite.op-add-types :as op-add-types]
            [com.viasat.halite.op-add-value-fields :as op-add-value-fields]
            [com.viasat.halite.op-canon :as op-canon]
            [com.viasat.halite.op-canon-refinements :as op-canon-refinements]
            [com.viasat.halite.op-conjoin-spec-bom :as op-conjoin-spec-bom]
            [com.viasat.halite.op-contradictions :as op-contradictions]
            [com.viasat.halite.op-ensure-fields :as op-ensure-fields]
            [com.viasat.halite.op-find-concrete :as op-find-concrete]
            [com.viasat.halite.op-find-refinements :as op-find-refinements]
            [com.viasat.halite.op-mandatory :as op-mandatory]
            [com.viasat.halite.op-push-down-to-concrete :as op-push-down-to-concrete]
            [com.viasat.halite.op-refinement-constraints :as op-refinement-constraints]
            [com.viasat.halite.op-refinements :as op-refinements]
            [com.viasat.halite.op-syntax-check :as op-syntax-check]
            [com.viasat.halite.op-type-check :as op-type-check]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn check-input-bom
  [spec-env
   bom :- bom-user/UserBom]
  (->> bom
       (op-syntax-check/syntax-check-op spec-env)
       (op-type-check/type-check-op spec-env)
       op-canon/canon-op
       (op-mandatory/mandatory-op spec-env)))

(s/defn expand-bom
  [spec-env
   bom :- bom/Bom]
  (->> bom
       (op-find-concrete/find-concrete-op spec-env)
       op-push-down-to-concrete/push-down-to-concrete-op
       (op-canon-refinements/canon-refinements-op spec-env)

       (op-conjoin-spec-bom/conjoin-spec-bom-op spec-env)
       (op-find-concrete/find-concrete-op spec-env)
       op-push-down-to-concrete/push-down-to-concrete-op
       (op-canon-refinements/canon-refinements-op spec-env)
       (op-find-refinements/find-refinements-op spec-env)
       ;; (op-refinements/refinements-op spec-env)
       op-contradictions/bubble-up-contradictions
       (op-add-types/add-types-op spec-env)
       (op-ensure-fields/ensure-fields-op spec-env)
       (op-add-value-fields/add-value-fields-op spec-env)
       (op-add-constraints/add-constraints-op spec-env)
       (op-refinement-constraints/refinement-constraints-op spec-env)))

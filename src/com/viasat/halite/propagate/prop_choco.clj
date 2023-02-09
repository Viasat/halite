;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-choco
  "Handles propagation for single specs that can be directly mapped to com.viasat.halite.choco-clj-opt.
  Specifically: single specs with (possibly optional) boolean or integer variables,
  and no refinements."
  (:require [com.viasat.halite.choco-clj-opt :as choco-clj]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.h-err :as h-err]
            [com.viasat.halite.lib.format-errors :refer [throw-err]]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.types :as types]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defschema AtomBound
  (s/cond-pre
   s/Int
   s/Bool
   (s/enum :Unset)
   {:$in (s/cond-pre
          #{(s/cond-pre s/Int s/Bool (s/enum :Unset))}
          [(s/one s/Int :lower) (s/one s/Int :upper) (s/optional (s/enum :Unset) :Unset)])}))

(s/defschema SpecBound
  {types/BareKeyword AtomBound})

(s/defn ^:private to-choco-type :- choco-clj/ChocoVarType
  [var-type :- types/HaliteType]
  (cond
    (= :Integer (types/no-maybe var-type)) :Int
    (= :Boolean (types/no-maybe var-type)) :Bool
    :else (throw (ex-info (format "BUG! Can't convert '%s' to choco var type" var-type) {:var-type var-type}))))

(defn- error->unsatisfiable
  [form]
  (cond
    (seq? form) (if (= 'error (first form))
                  (if (not (string? (second form)))
                    (throw (ex-info "BUG! Expressions other than string literals not currently supported as arguments to error"
                                    {:form form}))
                    (list 'unsatisfiable))
                  (apply list (first form) (map error->unsatisfiable (rest form))))
    (map? form) (update-vals form error->unsatisfiable)
    (vector? form) (mapv error->unsatisfiable form)
    :else form))

(s/defn ^:private lower-spec :- choco-clj/ChocoSpec
  [spec :- envs/SpecInfo]
  {:vars (-> spec :fields (update-keys symbol) (update-vals to-choco-type))
   :optionals (->> spec :fields
                   (filter (comp types/maybe-type?
                                 val))
                   (map (comp symbol key)) set)
   :constraints (->> spec :constraints (map (comp error->unsatisfiable second)) set)})

(s/defn ^:private lower-atom-bound :- choco-clj/VarBound
  [b :- AtomBound]
  (cond-> b (map? b) :$in))

(s/defn ^:private lower-spec-bound :- choco-clj/VarBounds
  [bound :- SpecBound]
  (-> bound (update-vals lower-atom-bound) (update-keys symbol)))

(s/defn ^:private raise-spec-bound :- SpecBound
  [bound :- choco-clj/VarBounds]
  (-> bound (update-keys keyword) (update-vals #(cond->> % (coll? %) (hash-map :$in)))))

(s/defschema Opts
  {:default-int-bounds [(s/one s/Int :lower) (s/one s/Int :upper)]})

(def default-options
  (s/validate
   Opts
   {:default-int-bounds [-1000 1000]}))

(s/defn propagate :- SpecBound
  ([spec :- ssa/SpecInfo, initial-bound :- SpecBound]
   (propagate spec default-options initial-bound))
  ([spec :- ssa/SpecInfo, opts :- Opts, initial-bound :- SpecBound]
   (try
     (binding [choco-clj/*default-int-bounds* (:default-int-bounds opts)]
       (-> spec
           (ssa/spec-from-ssa)
           (lower-spec)
           (choco-clj/propagate (lower-spec-bound initial-bound))
           (raise-spec-bound)))
     (catch org.chocosolver.solver.exception.ContradictionException ex
       (throw-err (h-err/no-valid-instance-in-bound {:initial-bound initial-bound}))))))

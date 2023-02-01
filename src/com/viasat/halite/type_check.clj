;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.type-check
  "halite type checker"
  (:require [clojure.set :as set]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.eval :as eval]
            [com.viasat.halite.h-err :as h-err]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.lib.format-errors :refer [throw-err text]]
            [com.viasat.halite.types :as types]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defschema TypeContext {:senv (s/protocol envs/SpecEnv) :tenv (s/protocol envs/TypeEnv)})

(s/defn type-check-instance :- types/HaliteType
  [check-fn :- clojure.lang.IFn, error-key :- s/Keyword, ctx :- TypeContext, inst :- {s/Keyword s/Any}]
  (let [t (or (:$type inst)
              (throw-err (h-err/missing-type-field {error-key inst})))
        _ (when-not (types/namespaced-keyword? t)
            (throw-err (h-err/invalid-type-value {error-key inst})))
        spec-info (or (envs/lookup-spec (:senv ctx) t)
                      (throw-err (h-err/resource-spec-not-found {:spec-id (symbol t)
                                                                 error-key inst})))
        field-types (:fields spec-info)
        fields (set (keys field-types))
        required-fields (->> field-types
                             (remove (comp types/maybe-type? val))
                             keys
                             set)
        supplied-fields (disj (set (keys inst)) :$type)
        missing-vars (set/difference required-fields supplied-fields)
        invalid-vars (set/difference supplied-fields fields)]

    (when (seq missing-vars)
      (throw-err (h-err/missing-required-vars {:missing-vars (mapv symbol missing-vars) :form inst})))
    (when (seq invalid-vars)
      (throw-err (h-err/field-name-not-in-spec {:invalid-vars (mapv symbol invalid-vars)
                                                :instance inst
                                                :form (get inst (first invalid-vars))})))

    ;; type-check variable values
    (doseq [[field-kw field-val] (dissoc inst :$type)]
      (let [field-type (get field-types field-kw)
            actual-type (check-fn ctx field-val)]
        (when-not (types/subtype? actual-type field-type)
          (throw-err (h-err/field-value-of-wrong-type {error-key inst
                                                       :variable (symbol field-kw)
                                                       :expected field-type
                                                       :actual actual-type})))
        (when (and (map? field-val)
                   (:abstract? (envs/lookup-spec (:senv ctx) (:$type field-val))))
          (throw-err (h-err/no-abstract {:value field-val
                                         :field field-kw
                                         :spec-id (symbol (:$type inst))
                                         :instance inst})))))
    (types/concrete-spec-type t)))

(s/defn ^:private get-typestring-for-coll [coll]
  (cond
    (vector? coll) "vector"
    (set? coll) "set"
    :else nil))

(s/defn type-check-coll :- types/HaliteType
  [check-fn :- clojure.lang.IFn
   error-key :- s/Keyword
   ctx :- TypeContext
   coll]
  (let [elem-types (map (partial check-fn ctx) coll)
        coll-type-string (get-typestring-for-coll coll)]
    (when (not coll-type-string)
      (throw-err (h-err/invalid-collection-type {error-key coll})))
    (doseq [[elem elem-type] (map vector coll elem-types)]
      (when (types/maybe-type? elem-type)
        (throw-err (h-err/literal-must-evaluate-to-value {:coll-type-string (symbol coll-type-string)
                                                          error-key elem}))))
    (types/vector-or-set-type coll (condp = (count coll)
                                     0 :Nothing
                                     1 (first elem-types)
                                     (reduce types/meet elem-types)))))

(defn type-check-fixed-decimal [value]
  (types/decimal-type (fixed-decimal/get-scale value)))

;;

(declare type-check*)

(s/defn matches-signature?
  [sig :- base/FnSignature, actual-types :- [types/HaliteType]]
  (let [{:keys [arg-types variadic-tail]} sig]
    (and
     (<= (count arg-types) (count actual-types))
     (every? true? (map #(types/subtype? %1 %2) actual-types arg-types))
     (or (and (= (count arg-types) (count actual-types))
              (nil? variadic-tail))
         (every? true? (map #(when variadic-tail (types/subtype? %1 variadic-tail))
                            (drop (count arg-types) actual-types)))))))

(s/defn ^:private type-check-fn-application :- types/HaliteType
  [ctx :- TypeContext, form :- [(s/one types/BareSymbol :op) s/Any]]
  (let [[op & args] form
        nargs (count args)
        {:keys [signatures impl] :as builtin} (get eval/builtins op)
        actual-types (map (partial type-check* ctx) args)]
    (when (nil? builtin)
      (throw-err (h-err/unknown-function-or-operator {:op op
                                                      :form form})))
    (loop [[sig & more] signatures]
      (cond
        (nil? sig) (throw-err (h-err/no-matching-signature {:op op
                                                            :form form
                                                            :actual-types actual-types
                                                            :signatures signatures}))
        (matches-signature? sig actual-types) (:return-type sig)
        :else (recur more)))))

(s/defn ^:private type-check-symbol :- types/HaliteType
  [ctx :- TypeContext, sym]
  (if (= '$no-value sym)
    :Unset
    (or (envs/lookup-type* (:tenv ctx) sym)
        (throw-err (h-err/undefined-symbol {:form sym})))))

(defn- arg-count-exactly
  [n form]
  (when (not= n (count (rest form)))
    (throw-err (h-err/wrong-arg-count {:op (first form)
                                       :expected-arg-count n
                                       :actual-arg-count (count (rest form))
                                       :form form}))))

(defn- arg-count-at-least
  [n form]
  (when (< (count (rest form)) n)
    (throw-err (h-err/wrong-arg-count-min {:op (first form)
                                           :minimum-arg-count n
                                           :actual-arg-count (count (rest form))
                                           :form form}))))

(defn- arg-count-odd
  [form]
  (when (not (= (mod (count (rest form)) 2) 1))
    (throw-err (h-err/wrong-arg-count-odd {:op (first form)
                                           :actual-arg-count (count (rest form))
                                           :form form}))))

(def ^:dynamic *lookup-f* nil)

(defn ^:private type-check-lookup [ctx form subexpr-type index]
  (when *lookup-f*
    (*lookup-f* subexpr-type index))
  (cond
    (types/halite-vector-type? subexpr-type)
    (do
      (when (keyword? index)
        (throw-err (h-err/invalid-vector-index {:form form :index-form index, :expected :Integer})))
      (let [index-type (type-check* ctx index)]
        (when (not= :Integer index-type)
          (throw-err (h-err/invalid-vector-index {:form form :index-form index, :expected :Integer, :actual-type index-type})))
        (types/elem-type subexpr-type)))

    (and (types/spec-type? subexpr-type)
         (types/spec-id subexpr-type)
         (not (types/needs-refinement? subexpr-type)))
    (let [field-types (->> subexpr-type types/spec-id (envs/lookup-spec (:senv ctx)) :fields)]
      (when-not (types/bare-keyword? index)
        (throw-err (h-err/invalid-instance-index {:form form, :index-form index})))
      (when-not (contains? field-types index)
        (throw-err (h-err/field-name-not-in-spec {:form form, :index-form index, :spec-id (symbol (types/spec-id subexpr-type)) :invalid-vars [(symbol index)]})))
      (get field-types index))

    :else (throw-err (h-err/invalid-lookup-target {:form form, :actual-type subexpr-type}))))

(s/defn ^:private type-check-get :- types/HaliteType
  [ctx :- TypeContext, form]
  (arg-count-exactly 2 form)
  (let [[_ subexpr index] form]
    (type-check-lookup ctx form (type-check* ctx subexpr) index)))

(s/defn ^:private type-check-get-in :- types/HaliteType
  [ctx :- TypeContext, form]
  (arg-count-exactly 2 form)
  (let [[_ subexpr indexes] form]
    (when-not (vector? indexes)
      (throw-err (h-err/get-in-path-must-be-vector-literal {:form form})))
    (reduce (partial type-check-lookup ctx form) (type-check* ctx subexpr) indexes)))

(s/defn ^:private type-check-equals :- types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (->> (map (partial type-check* ctx) (rest expr))
       dorun)
  :Boolean)

(defn add-position [n m]
  (let [m' (assoc m
                  :position-text (text (condp = n
                                         nil "Argument"
                                         0 "First argument"
                                         1 "Second argument"
                                         "An argument")))]
    (if (nil? n)
      m'
      (assoc m' :position n))))

(s/defn ^:private type-check-set-scale :- types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 2 expr)
  (let [[_ _ scale] expr
        arg-types (mapv (partial type-check* ctx) (rest expr))]
    (when-not (types/decimal-type? (first arg-types))
      (throw-err (h-err/arg-type-mismatch (add-position 0 {:op 'rescale :expected-type-description (text "a fixed point decimal") :expr expr}))))
    (when-not (= :Integer (second arg-types))
      (throw-err (h-err/arg-type-mismatch (add-position 1 {:op 'rescale :expected-type-description (text "an integer") :expr expr}))))
    (when-not (base/integer-or-long? scale)
      (throw-err (h-err/arg-type-mismatch (add-position 1 {:op 'rescale :expected-type-description (text "an integer literal") :expr expr}))))
    (when-not (and (>= scale 0)
                   (< scale (inc fixed-decimal/max-scale)))
      (throw-err (h-err/arg-type-mismatch (add-position 1 {:op 'rescale
                                                           :expected-type-description (text (format "an integer between 0 and %s" fixed-decimal/max-scale))
                                                           :expr expr}))))
    (if (zero? scale)
      :Integer
      (types/decimal-type scale))))

(s/defn ^:private type-check-if :- types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 3 expr)
  (let [[pred-type s t] (mapv (partial type-check* ctx) (rest expr))]
    (when (not= :Boolean pred-type)
      (throw-err (h-err/arg-type-mismatch (add-position 0 {:op 'if :expected-type-description (text "boolean") :expr expr}))))
    (types/meet s t)))

(s/defn ^:private type-check-cond :- types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-odd expr)
  (arg-count-at-least 3 expr)
  (loop [[pred-type t & more] (mapv (partial type-check* ctx) (rest expr))
         s nil]
    (if t
      (do
        (when (not= :Boolean pred-type)
          (throw-err (h-err/arg-type-mismatch (add-position 0 {:op 'cond :expected-type-description (text "boolean") :expr expr}))))
        (recur more (if s
                      (types/meet s t)
                      t)))
      (types/meet pred-type s))))

(s/defn ^:private type-check-when :- types/HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[pred-type body-type] (map (partial type-check* ctx) (rest expr))]
    (when (not= :Boolean pred-type)
      (throw-err (h-err/arg-type-mismatch (add-position 0 {:op 'when :expected-type-description (text "boolean") :expr expr}))))
    (types/maybe-type body-type)))

(s/defn ^:private type-check-let :- types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 2 expr)
  (let [[bindings body] (rest expr)]
    (when-not (zero? (mod (count bindings) 2))
      (throw-err (h-err/let-bindings-odd-count {:form expr})))
    (type-check*
     (reduce
      (fn [ctx [sym body]]
        (when-not (types/bare-symbol? sym)
          (throw-err (h-err/let-needs-bare-symbol {:form expr})))
        (when (base/reserved-words sym)
          (throw-err (h-err/cannot-bind-reserved-word {:sym sym
                                                       :form expr})))
        (update ctx :tenv envs/extend-scope sym (type-check* ctx body)))
      ctx
      (partition 2 bindings))
     body)))

(s/defn ^:private type-check-comprehend
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[op [sym expr :as bindings] body] expr]
    (when-not (= 2 (count bindings))
      (throw-err (h-err/comprehend-binding-wrong-count {:op op :form expr})))
    (when-not (types/bare-symbol? sym)
      (throw-err (h-err/binding-target-must-be-bare-symbol {:op op :form expr :sym sym})))
    (let [coll-type (type-check* ctx expr)
          et (types/elem-type coll-type)
          _ (when-not et
              (throw-err (h-err/comprehend-collection-invalid-type {:op op, :expr-type coll-type, :form expr
                                                                    :actual-type (or (types/spec-id coll-type) coll-type)})))
          body-type (type-check* (update ctx :tenv envs/extend-scope sym et) body)]
      {:coll-type coll-type
       :body-type body-type})))

(s/defn ^:private type-check-quantifier :- types/HaliteType
  [ctx :- TypeContext, expr]
  (when (not= :Boolean (:body-type (type-check-comprehend ctx expr)))
    (throw-err (h-err/not-boolean-body {:op (first expr)
                                        :form expr})))
  :Boolean)

(s/defn ^:private type-check-map :- types/HaliteType
  [ctx :- TypeContext, expr]
  (let [{:keys [coll-type body-type]} (type-check-comprehend ctx expr)]
    (when (types/maybe-type? body-type)
      (throw-err (h-err/must-produce-value {:form expr})))
    [(first coll-type) (if (some #(types/subtype? coll-type %) types/empty-colls)
                         :Nothing
                         body-type)]))

(s/defn ^:private type-check-filter :- types/HaliteType
  [ctx :- TypeContext, expr]
  (let [{:keys [coll-type body-type]} (type-check-comprehend ctx expr)]
    (when (not= :Boolean body-type)
      (throw-err (h-err/not-boolean-body {:op 'filter
                                          :form expr})))
    coll-type))

(s/defn ^:private type-check-sort-by :- types/HaliteType
  [ctx :- TypeContext, expr]
  (let [{:keys [coll-type body-type]} (type-check-comprehend ctx expr)]
    (when-not (or (= :Integer body-type)
                  (types/decimal-type? body-type))
      (throw-err (h-err/not-sortable-body {:op 'sort-by :form expr :actual-type body-type})))
    (types/vector-type (types/elem-type coll-type))))

(s/defn ^:private type-check-reduce :- types/HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 3 expr)
  (let [[op [acc init] [elem coll] body] expr]
    (when-not (types/bare-symbol? acc)
      (throw-err (h-err/accumulator-target-must-be-bare-symbol {:op op, :accumulator acc, :form expr})))
    (when-not (types/bare-symbol? elem)
      (throw-err (h-err/element-binding-target-must-be-bare-symbol {:op op, :form expr, :element elem})))
    (when (= acc elem)
      (throw-err (h-err/element-accumulator-same-symbol {:form expr, :accumulator acc, :element elem})))
    (let [init-type (type-check* ctx init)
          coll-type (type-check* ctx coll)
          et (types/elem-type coll-type)]
      (when-not (types/subtype? coll-type (types/vector-type :Value))
        (throw-err (h-err/reduce-not-vector {:form expr, :actual-coll-type coll-type})))
      (type-check* (update ctx :tenv #(-> %
                                          (envs/extend-scope acc init-type)
                                          (envs/extend-scope elem et)))
                   body))))

(s/defn ^:private type-check-if-value :- types/HaliteType
  "handles if-value and when-value"
  [ctx :- TypeContext, expr :- s/Any]
  (let [[op sym set-expr unset-expr] expr]
    (arg-count-exactly (if (= 'when-value op) 2 3) expr)
    (when-not (types/bare-symbol? sym)
      (throw-err (h-err/if-value-must-be-bare-symbol {:op op
                                                      :form expr})))
    (let [sym-type (type-check* ctx sym)
          unset-type (if (= 'when-value op)
                       :Unset
                       (type-check* (update ctx :tenv envs/extend-scope sym :Unset) unset-expr))]
      (if (= :Unset sym-type)
        unset-type
        (let [inner-type (types/no-maybe sym-type)
              set-type (type-check* (update ctx :tenv envs/extend-scope sym inner-type) set-expr)]
          (types/meet set-type unset-type))))))

(s/defn ^:private type-check-if-value-let :- types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (let [[op [sym maybe-expr] then-expr else-expr] expr]
    (arg-count-exactly (if (= 'when-value-let op) 2 3) expr)
    (when-not (types/bare-symbol? sym)
      (throw-err (h-err/binding-target-must-be-bare-symbol {:op op
                                                            :sym sym})))
    (let [maybe-type (type-check* ctx maybe-expr)
          else-type (if (= 'when-value-let op)
                      :Unset
                      (type-check* (update ctx :tenv envs/extend-scope sym :Unset) else-expr))]
      (if (= :Unset maybe-type)
        else-type
        (let [inner-type (types/no-maybe maybe-type)
              then-type (type-check* (update ctx :tenv envs/extend-scope sym inner-type) then-expr)]
          (types/meet then-type else-type))))))

(defn check-all-sets [[op :as expr] arg-types]
  (when-not (every? #(types/subtype? % (types/set-type :Value)) arg-types)
    (throw-err (h-err/arguments-not-sets {:op op, :form expr}))))

(s/defn ^:private type-check-union :- types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (check-all-sets expr arg-types)
    (reduce types/meet types/empty-set arg-types)))

(s/defn ^:private type-check-intersection :- types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (check-all-sets expr arg-types)
    (if (empty? arg-types)
      types/empty-set
      (reduce types/join arg-types))))

(s/defn ^:private type-check-difference :- types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (check-all-sets expr arg-types)
    (first arg-types)))

(s/defn ^:private type-check-first :- types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 1 expr)
  (let [arg-type (type-check* ctx (second expr))]
    (when-not (or (types/subtype? arg-type (types/vector-type :Value))
                  (types/subtype? arg-type (types/set-type :Value)))
      (throw-err (h-err/argument-not-collection {:op 'first, :form expr})))
    (when (or (types/empty-vectors arg-type)
              (= types/empty-set arg-type))
      (throw-err (h-err/argument-empty {:form expr})))
    (second arg-type)))

(s/defn ^:private type-check-rest :- types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 1 expr)
  (let [arg-type (type-check* ctx (second expr))]
    (when-not (types/subtype? arg-type (types/vector-type :Value))
      (throw-err (h-err/argument-not-vector {:op 'rest, :form expr})))
    arg-type))

(s/defn ^:private type-check-conj :- types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (let [[base-type & elem-types] (mapv (partial type-check* ctx) (rest expr))]
    (when-not (types/subtype? base-type (types/coll-type :Value))
      (throw-err (h-err/argument-not-set-or-vector {:form expr})))
    (doseq [[elem elem-type] (map vector (drop 2 expr) elem-types)]
      (when (types/maybe-type? elem-type)
        (throw-err (h-err/cannot-conj-unset {:type-string (symbol (types/coll-type-string base-type)), :form elem}))))
    (types/change-elem-type
     base-type
     (reduce types/meet (types/elem-type base-type) elem-types))))

(s/defn ^:private type-check-concat :- types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 2 expr)
  (let [op (first expr)
        [s t] (mapv (partial type-check* ctx) (rest expr))]
    (when-not (types/subtype? s (types/coll-type :Value))
      (throw-err (h-err/arg-type-mismatch (add-position 0 {:op op :expected-type-description (text "a set or vector"), :form expr}))))
    (when-not (types/subtype? t (types/coll-type :Value))
      (throw-err (h-err/arg-type-mismatch (add-position 1 {:op op :expected-type-description (text "a set or vector"), :form expr}))))
    (when (and (types/subtype? s (types/vector-type :Value)) (not (types/subtype? t (types/vector-type :Value))))
      (throw-err (h-err/not-both-vectors {:op op, :form expr})))
    (types/meet s
                (types/change-elem-type s (types/elem-type t)))))

(s/defn ^:private type-check-refine-to :- types/HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[subexpr kw] (rest expr)
        s (type-check* ctx subexpr)]
    (when-not (types/subtype? s (types/instance-type))
      (throw-err (h-err/arg-type-mismatch (add-position 0 {:op 'refine-to :expected-type-description (text "an instance"), :form expr, :actual s}))))
    (when-not (types/namespaced-keyword? kw)
      (throw-err (h-err/arg-type-mismatch (add-position 1 {:op 'refine-to :expected-type-description (text "a spec id"), :form expr}))))
    (when-not (envs/lookup-spec (:senv ctx) kw)
      (throw-err (h-err/resource-spec-not-found {:spec-id (symbol kw) :form expr})))
    (types/concrete-spec-type kw)))

(s/defn ^:private type-check-refines-to? :- types/HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[subexpr kw] (rest expr)
        s (type-check* ctx subexpr)]
    (when-not (types/subtype? s (types/instance-type))
      (throw-err (h-err/arg-type-mismatch (add-position 0 {:op 'refines-to? :expected-type-description (text "an instance"), :form expr}))))
    (when-not (types/namespaced-keyword? kw)
      (throw-err (h-err/arg-type-mismatch (add-position 1 {:op 'refines-to? :expected-type-description (text "a spec id"), :form expr}))))
    (when-not (envs/lookup-spec (:senv ctx) kw)
      (throw-err (h-err/resource-spec-not-found {:spec-id (symbol kw) :form expr})))
    :Boolean))

(s/defn ^:private type-check-valid :- types/HaliteType
  [ctx :- TypeContext, [_valid subexpr :as expr]]
  (arg-count-exactly 1 expr)
  (let [t (type-check* ctx subexpr)]
    (cond
      (types/spec-type? t) (types/maybe-type t)
      ;; questionable...
      ;;(and (vector? t) (= :Maybe (first t)) (spec-type? (second t))) t
      :else (throw-err (h-err/arg-type-mismatch (add-position nil {:op 'valid, :expected-type-description (text "an instance of known type"), :form expr}))))))

(s/defn ^:private type-check-valid? :- types/HaliteType
  [ctx :- TypeContext, [_valid? subexpr :as expr]]
  (arg-count-exactly 1 expr)
  (let [t (type-check* ctx subexpr)]
    (cond
      (types/spec-type? t) :Boolean
      ;; questionable...
      ;;(and (vector? t) (= :Maybe (first t)) (spec-type? (second t))) :Boolean
      :else (throw-err (h-err/arg-type-mismatch (add-position nil {:op 'valid?, :expected-type-description (text "an instance of known type"), :form expr}))))))

(s/defn type-check* :- types/HaliteType
  [ctx :- TypeContext
   expr]
  (let [type (cond
               (boolean? expr) :Boolean
               (base/integer-or-long? expr) :Integer
               (base/fixed-decimal? expr) (type-check-fixed-decimal expr)
               (string? expr) :String
               (symbol? expr) (type-check-symbol ctx expr)
               (map? expr) (type-check-instance type-check* :form ctx expr)
               (seq? expr) (condp = (first expr)
                             'get (type-check-get ctx expr)
                             'get-in (type-check-get-in ctx expr)
                             '= (type-check-equals ctx expr)
                             'not= (type-check-equals ctx expr) ; = and not= have same typing rule
                             'rescale (type-check-set-scale ctx expr)
                             'if (type-check-if ctx expr)
                             'cond (type-check-cond ctx expr)
                             'when (type-check-when ctx expr)
                             'let (type-check-let ctx expr)
                             'if-value (type-check-if-value ctx expr)
                             'when-value (type-check-if-value ctx expr) ; if-value type-checks when-value
                             'if-value-let (type-check-if-value-let ctx expr)
                             'when-value-let (type-check-if-value-let ctx expr) ; if-value-let type-checks when-value-let
                             'union (type-check-union ctx expr)
                             'intersection (type-check-intersection ctx expr)
                             'difference (type-check-difference ctx expr)
                             'first (type-check-first ctx expr)
                             'rest (type-check-rest ctx expr)
                             'conj (type-check-conj ctx expr)
                             'concat (type-check-concat ctx expr)
                             'refine-to (type-check-refine-to ctx expr)
                             'refines-to? (type-check-refines-to? ctx expr)
                             'every? (type-check-quantifier ctx expr)
                             'any? (type-check-quantifier ctx expr)
                             'map (type-check-map ctx expr)
                             'filter (type-check-filter ctx expr)
                             'valid (type-check-valid ctx expr)
                             'valid? (type-check-valid? ctx expr)
                             'sort-by (type-check-sort-by ctx expr)
                             'reduce (type-check-reduce ctx expr)
                             (type-check-fn-application ctx expr))
               (coll? expr) (type-check-coll type-check* :form ctx expr)
               :else (throw-err (h-err/syntax-error {:form expr, :form-class (class expr)})))]
    (when (and (or (types/halite-vector-type? type)
                   (types/halite-set-type? type))
               (types/value-type? (types/elem-type type)))
      (throw-err (h-err/unknown-type-collection {:form expr})))
    type))

(s/defn type-check :- types/HaliteType
  "Return the type of the expression, or throw an error if the form is syntactically invalid,
  or not well typed in the given typ environment."
  [senv :- (s/protocol envs/SpecEnv) tenv :- (s/protocol envs/TypeEnv) expr :- s/Any]
  (type-check* {:senv senv :tenv tenv} expr))

(s/defn type-check-constraint-expr
  [senv tenv bool-expr]
  (let [t (type-check* {:senv senv :tenv tenv} bool-expr)]
    (when (not= :Boolean t)
      (throw-err (h-err/not-boolean-constraint {:type t})))))

(s/defn type-check-refinement-expr
  [senv tenv spec-id expr]
  (let [expected-type (types/maybe-type (types/concrete-spec-type spec-id))
        t (type-check* {:senv senv :tenv tenv} expr)]
    (when-not (types/subtype? t expected-type)
      (throw-err (h-err/invalid-refinement-expression {:form expr
                                                       :expected-type spec-id
                                                       :actual-type t})))))

(s/defn type-check-spec
  [senv :- (s/protocol envs/SpecEnv)
   spec-info :- envs/SpecInfo]
  (let [{:keys [constraints refines-to]} spec-info
        tenv (envs/type-env-from-spec spec-info)]
    (doseq [[cname cexpr] constraints]
      (type-check-constraint-expr senv tenv cexpr))
    (doseq [[spec-id {:keys [expr name]}] refines-to]
      (type-check-refinement-expr senv tenv spec-id expr))))

;;

(s/defn find-field-accesses
  [senv :- (s/protocol envs/SpecEnv)
   spec-info :- envs/SpecInfo
   expr]
  (let [lookups-atom (atom #{})]
    (binding [*lookup-f* (fn [halite-type index]
                           (when-let [spec-id (types/spec-id halite-type)]
                             (swap! lookups-atom conj {:spec-id spec-id
                                                       :variable-name index})))]
      (let [tenv (envs/type-env-from-spec spec-info)]
        (type-check senv tenv expr))
      @lookups-atom)))

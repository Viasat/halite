;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite
  "Expression language for resource spec constraints and refinements that is almost, but
  not quite, a drop-in replacement for salt."
  (:require [clojure.set :as set]
            [jibe.h-err :as h-err]
            [jibe.halite-base :as halite-base]
            [jibe.halite-eval :as halite-eval]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite-syntax-check :as halite-syntax-check]
            [jibe.lib.fixed-decimal :as fixed-decimal]
            [jibe.lib.format-errors :refer [throw-err with-exception-data text]]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

;;

(declare type-check*)

(s/defn ^:private eval-predicate :- Boolean
  [ctx :- halite-eval/EvalContext, tenv :- (s/protocol halite-envs/TypeEnv), err-msg :- String, bool-expr]
  ;; TODO: currently this with-exception-data form causes err-msg to be thrown without an error code
  (with-exception-data err-msg {:form bool-expr}
    (let [t (type-check* {:senv (:senv ctx) :tenv tenv} bool-expr)]
      (when (not= :Boolean t)
        (throw-err (h-err/not-boolean-constraint {:type t})))))
  (halite-eval/eval-expr* ctx bool-expr))

(s/defn ^:private eval-refinement :- (s/maybe s/Any)
  "Returns an instance of type spec-id, projected from the instance vars in ctx,
  or nil if the guards prevent this projection."
  [ctx :- halite-eval/EvalContext, tenv :- (s/protocol halite-envs/TypeEnv),  spec-id :- halite-types/NamespacedKeyword, expr]
  (if (contains? halite-eval/*refinements* spec-id)
    (halite-eval/*refinements* spec-id) ;; cache hit
    (let [expected-type (halite-types/maybe-type (halite-types/concrete-spec-type spec-id))
          t (type-check* {:senv (:senv ctx) :tenv tenv} expr)]
      (when-not (halite-types/subtype? t expected-type)
        (throw-err (h-err/invalid-refinement-expression {:form expr
                                                         :declared-type expected-type
                                                         :actual-type t})))
      (halite-eval/eval-expr* ctx expr))))

(defmacro with-eval-bindings [form]
  `(binding [halite-eval/*eval-predicate-fn* eval-predicate
             halite-eval/*eval-refinement-fn* eval-refinement]
     ~form))

(s/defschema ^:private TypeContext {:senv (s/protocol halite-envs/SpecEnv) :tenv (s/protocol halite-envs/TypeEnv)})

(s/defn check-instance :- halite-types/HaliteType
  [check-fn :- clojure.lang.IFn, error-key :- s/Keyword, ctx :- TypeContext, inst :- {s/Keyword s/Any}]
  (let [t (or (:$type inst)
              (throw-err (h-err/missing-type-field {error-key inst})))
        _ (when-not (halite-types/namespaced-keyword? t)
            (throw-err (h-err/invalid-type-value {error-key inst})))
        spec-info (or (halite-envs/lookup-spec (:senv ctx) t)
                      (throw-err (h-err/resource-spec-not-found {:spec-id (symbol t)
                                                                 error-key inst})))
        field-types (:spec-vars spec-info)
        fields (set (keys field-types))
        required-fields (->> field-types
                             (remove (comp halite-envs/optional-var-type? val))
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
      (let [field-type (halite-envs/halite-type-from-var-type (:senv ctx) (get field-types field-kw))
            actual-type (check-fn ctx field-val)]
        (when-not (halite-types/subtype? actual-type field-type)
          (throw-err (h-err/field-value-of-wrong-type {error-key inst
                                                       :variable (symbol field-kw)
                                                       :expected field-type
                                                       :actual actual-type})))))
    (halite-types/concrete-spec-type t)))

(s/defn ^:private get-typestring-for-coll [coll]
  (cond
    (vector? coll) "vector"
    (set? coll) "set"
    :else nil))

(s/defn check-coll :- halite-types/HaliteType
  [check-fn :- clojure.lang.IFn, error-key :- s/Keyword, ctx :- TypeContext, coll]
  (let [elem-types (map (partial check-fn ctx) coll)
        coll-type-string (get-typestring-for-coll coll)]
    (when (not coll-type-string)
      (throw-err (h-err/invalid-collection-type {error-key coll})))
    (doseq [[elem elem-type] (map vector coll elem-types)]
      (when (halite-types/maybe-type? elem-type)
        (throw-err (h-err/literal-must-evaluate-to-value {:coll-type-string (symbol coll-type-string)
                                                          error-key elem}))))
    (halite-types/vector-or-set-type coll (condp = (count coll)
                                            0 :Nothing
                                            1 (first elem-types)
                                            (reduce halite-types/meet elem-types)))))

(defn type-check-fixed-decimal [value]
  (halite-types/decimal-type (fixed-decimal/get-scale value)))

(s/defn ^:private type-of* :- halite-types/HaliteType
  [ctx :- TypeContext, value]
  (cond
    (boolean? value) :Boolean
    (halite-base/integer-or-long? value) :Integer
    (halite-base/fixed-decimal? value) (type-check-fixed-decimal value)
    (string? value) :String
    (= :Unset value) :Unset
    (map? value) (let [t (check-instance type-of* :value ctx value)]
                   (with-eval-bindings
                     (halite-eval/validate-instance (:senv ctx) value))
                   t)
    (coll? value) (check-coll type-of* :value ctx value)
    :else (throw-err (h-err/invalid-value {:value value}))))

(s/defn type-of :- halite-types/HaliteType
  "Return the type of the given runtime value, or throw an error if the value is invalid and cannot be typed.
  For instances, this function checks all applicable constraints. Any constraint violations result in a thrown exception."
  [senv :- (s/protocol halite-envs/SpecEnv), tenv :- (s/protocol halite-envs/TypeEnv), value :- s/Any]
  (type-of* {:senv senv :tenv tenv} value))

(defn check-n [object-type n v error-context]
  (when (and n
             (> v n))
    (throw-err (h-err/limit-exceeded {:object-type object-type
                                      :value v
                                      :limit n}))))

;; 

(s/defn matches-signature?
  [sig :- halite-base/FnSignature, actual-types :- [halite-types/HaliteType]]
  (let [{:keys [arg-types variadic-tail]} sig]
    (and
     (<= (count arg-types) (count actual-types))
     (every? true? (map #(halite-types/subtype? %1 %2) actual-types arg-types))
     (or (and (= (count arg-types) (count actual-types))
              (nil? variadic-tail))
         (every? true? (map #(when variadic-tail (halite-types/subtype? %1 variadic-tail))
                            (drop (count arg-types) actual-types)))))))

(s/defn ^:private type-check-fn-application :- halite-types/HaliteType
  [ctx :- TypeContext, form :- [(s/one halite-types/BareSymbol :op) s/Any]]
  (let [[op & args] form
        nargs (count args)
        {:keys [signatures impl] :as builtin} (get halite-eval/builtins op)
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

(s/defn ^:private type-check-symbol :- halite-types/HaliteType
  [ctx :- TypeContext, sym]
  (if (= '$no-value sym)
    :Unset
    (or (get (halite-envs/scope (:tenv ctx)) sym)
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

(defn ^:private type-check-lookup [ctx form subexpr-type index]
  (cond
    (halite-types/halite-vector-type? subexpr-type)
    (do
      (when (keyword? index)
        (throw-err (h-err/invalid-vector-index {:form form :index-form index, :expected :Integer})))
      (let [index-type (type-check* ctx index)]
        (when (not= :Integer index-type)
          (throw-err (h-err/invalid-vector-index {:form form :index-form index, :expected :Integer, :actual-type index-type})))
        (halite-types/elem-type subexpr-type)))

    (and (halite-types/spec-type? subexpr-type)
         (halite-types/spec-id subexpr-type)
         (not (halite-types/needs-refinement? subexpr-type)))
    (let [field-types (-> (->> subexpr-type halite-types/spec-id (halite-envs/lookup-spec (:senv ctx)) :spec-vars)
                          (update-vals (partial halite-envs/halite-type-from-var-type (:senv ctx))))]
      (when-not (and (keyword? index) (halite-types/bare? index))
        (throw-err (h-err/invalid-instance-index {:form form, :index-form index})))
      (when-not (contains? field-types index)
        (throw-err (h-err/field-name-not-in-spec {:form form, :index-form index, :spec-id (symbol (halite-types/spec-id subexpr-type)) :invalid-vars [(symbol index)]})))
      (get field-types index))

    :else (throw-err (h-err/invalid-lookup-target {:form form, :actual-type subexpr-type}))))

(s/defn ^:private type-check-get :- halite-types/HaliteType
  [ctx :- TypeContext, form]
  (arg-count-exactly 2 form)
  (let [[_ subexpr index] form]
    (type-check-lookup ctx form (type-check* ctx subexpr) index)))

(s/defn ^:private type-check-get-in :- halite-types/HaliteType
  [ctx :- TypeContext, form]
  (arg-count-exactly 2 form)
  (let [[_ subexpr indexes] form]
    (when-not (vector? indexes)
      (throw-err (h-err/get-in-path-must-be-vector-literal {:form form})))
    (reduce (partial type-check-lookup ctx form) (type-check* ctx subexpr) indexes)))

(s/defn ^:private type-check-equals :- halite-types/HaliteType
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

(s/defn ^:private type-check-set-scale :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 2 expr)
  (let [[_ _ scale] expr
        arg-types (mapv (partial type-check* ctx) (rest expr))]
    (when-not (halite-types/decimal-type? (first arg-types))
      (throw-err (h-err/arg-type-mismatch (add-position 0 {:op 'rescale :expected-type-description (text "a fixed point decimal") :expr expr}))))
    (when-not (= :Integer (second arg-types))
      (throw-err (h-err/arg-type-mismatch (add-position 1 {:op 'rescale :expected-type-description (text "an integer") :expr expr}))))
    (when-not (halite-base/integer-or-long? scale)
      (throw-err (h-err/arg-type-mismatch (add-position 1 {:op 'rescale :expected-type-description (text "an integer literal") :expr expr}))))
    (when-not (and (>= scale 0)
                   (< scale (inc fixed-decimal/max-scale)))
      (throw-err (h-err/arg-type-mismatch (add-position 1 {:op 'rescale
                                                           :expected-type-description (text (format "an integer between 0 and %s" fixed-decimal/max-scale))
                                                           :expr expr}))))
    (if (zero? scale)
      :Integer
      (halite-types/decimal-type scale))))

(s/defn ^:private type-check-if :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 3 expr)
  (let [[pred-type s t] (mapv (partial type-check* ctx) (rest expr))]
    (when (not= :Boolean pred-type)
      (throw-err (h-err/arg-type-mismatch (add-position 0 {:op 'if :expected-type-description (text "boolean") :expr expr}))))
    (halite-types/meet s t)))

(s/defn ^:private type-check-when :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[pred-type body-type] (map (partial type-check* ctx) (rest expr))]
    (when (not= :Boolean pred-type)
      (throw-err (h-err/arg-type-mismatch (add-position 0 {:op 'when :expected-type-description (text "boolean") :expr expr}))))
    (halite-types/maybe-type body-type)))

(s/defn ^:private type-check-let :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 2 expr)
  (let [[bindings body] (rest expr)]
    (when-not (zero? (mod (count bindings) 2))
      (throw-err (h-err/let-bindings-odd-count {:form expr})))
    (type-check*
     (reduce
      (fn [ctx [sym body]]
        (when-not (and (symbol? sym) (halite-types/bare? sym))
          (throw-err (h-err/let-needs-bare-symbol {:form expr})))
        (when (halite-base/reserved-words sym)
          (throw-err (h-err/cannot-bind-reserved-word {:sym sym
                                                       :form expr})))
        (update ctx :tenv halite-envs/extend-scope sym (type-check* ctx body)))
      ctx
      (partition 2 bindings))
     body)))

(s/defn ^:private type-check-comprehend
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[op [sym expr :as bindings] body] expr]
    (when-not (= 2 (count bindings))
      (throw-err (h-err/comprehend-binding-wrong-count {:op op :form expr})))
    (when-not (and (symbol? sym) (halite-types/bare? sym))
      (throw-err (h-err/binding-target-must-be-bare-symbol {:op op :form expr :sym sym})))
    (let [coll-type (type-check* ctx expr)
          et (halite-types/elem-type coll-type)
          _ (when-not et
              (throw-err (h-err/comprehend-collection-invalid-type {:op op, :expr-type coll-type, :form expr
                                                                    :actual-type (or (halite-types/spec-id coll-type) coll-type)})))
          body-type (type-check* (update ctx :tenv halite-envs/extend-scope sym et) body)]
      {:coll-type coll-type
       :body-type body-type})))

(s/defn ^:private type-check-quantifier :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (when (not= :Boolean (:body-type (type-check-comprehend ctx expr)))
    (throw-err (h-err/not-boolean-body {:op (first expr)
                                        :form expr})))
  :Boolean)

(s/defn ^:private type-check-map :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (let [{:keys [coll-type body-type]} (type-check-comprehend ctx expr)]
    (when (halite-types/maybe-type? body-type)
      (throw-err (h-err/must-produce-value {:form expr})))
    [(first coll-type) (if (some #(halite-types/subtype? coll-type %) halite-types/empty-colls)
                         :Nothing
                         body-type)]))

(s/defn ^:private type-check-filter :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (let [{:keys [coll-type body-type]} (type-check-comprehend ctx expr)]
    (when (not= :Boolean body-type)
      (throw-err (h-err/not-boolean-body {:op 'filter
                                          :form expr})))
    coll-type))

(s/defn ^:private type-check-sort-by :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (let [{:keys [coll-type body-type]} (type-check-comprehend ctx expr)]
    (when-not (or (= :Integer body-type)
                  (halite-types/decimal-type? body-type))
      (throw-err (h-err/not-sortable-body {:op 'sort-by :form expr :actual-type body-type})))
    (halite-types/vector-type (halite-types/elem-type coll-type))))

(s/defn ^:private type-check-reduce :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 3 expr)
  (let [[op [acc init] [elem coll] body] expr]
    (when-not (and (symbol? acc) (halite-types/bare? acc))
      (throw-err (h-err/accumulator-target-must-be-bare-symbol {:op op, :accumulator acc, :form expr})))
    (when-not (and (symbol? elem) (halite-types/bare? elem))
      (throw-err (h-err/element-binding-target-must-be-bare-symbol {:op op, :form expr, :element elem})))
    (when (= acc elem)
      (throw-err (h-err/element-accumulator-same-symbol {:form expr, :accumulator acc, :element elem})))
    (let [init-type (type-check* ctx init)
          coll-type (type-check* ctx coll)
          et (halite-types/elem-type coll-type)]
      (when-not (halite-types/subtype? coll-type (halite-types/vector-type :Value))
        (throw-err (h-err/reduce-not-vector {:form expr, :actual-coll-type coll-type})))
      (type-check* (update ctx :tenv #(-> %
                                          (halite-envs/extend-scope acc init-type)
                                          (halite-envs/extend-scope elem et)))
                   body))))

(s/defn ^:private type-check-if-value :- halite-types/HaliteType
  "handles if-value and when-value"
  [ctx :- TypeContext, expr :- s/Any]
  (let [[op sym set-expr unset-expr] expr]
    (arg-count-exactly (if (= 'when-value op) 2 3) expr)
    (when-not (and (symbol? sym) (halite-types/bare? sym))
      (throw-err (h-err/if-value-must-be-bare-symbol {:op op
                                                      :form expr})))
    (let [sym-type (type-check* ctx sym)
          unset-type (if (= 'when-value op)
                       :Unset
                       (type-check* (update ctx :tenv halite-envs/extend-scope sym :Unset) unset-expr))]
      (if (= :Unset sym-type)
        unset-type
        (let [inner-type (halite-types/no-maybe sym-type)
              set-type (type-check* (update ctx :tenv halite-envs/extend-scope sym inner-type) set-expr)]
          (halite-types/meet set-type unset-type))))))

(s/defn ^:private type-check-if-value-let :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (let [[op [sym maybe-expr] then-expr else-expr] expr]
    (arg-count-exactly (if (= 'when-value-let op) 2 3) expr)
    (when-not (and (symbol? sym) (halite-types/bare? sym))
      (throw-err (h-err/binding-target-must-be-bare-symbol {:op op
                                                            :sym sym})))
    (let [maybe-type (type-check* ctx maybe-expr)
          else-type (if (= 'when-value-let op)
                      :Unset
                      (type-check* (update ctx :tenv halite-envs/extend-scope sym :Unset) else-expr))]
      (if (= :Unset maybe-type)
        else-type
        (let [inner-type (halite-types/no-maybe maybe-type)
              then-type (type-check* (update ctx :tenv halite-envs/extend-scope sym inner-type) then-expr)]
          (halite-types/meet then-type else-type))))))

(defn check-all-sets [[op :as expr] arg-types]
  (when-not (every? #(halite-types/subtype? % (halite-types/set-type :Value)) arg-types)
    (throw-err (h-err/arguments-not-sets {:op op, :form expr}))))

(s/defn ^:private type-check-union :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (check-all-sets expr arg-types)
    (reduce halite-types/meet halite-types/empty-set arg-types)))

(s/defn ^:private type-check-intersection :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (check-all-sets expr arg-types)
    (if (empty? arg-types)
      halite-types/empty-set
      (reduce halite-types/join arg-types))))

(s/defn ^:private type-check-difference :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (check-all-sets expr arg-types)
    (first arg-types)))

(s/defn ^:private type-check-first :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 1 expr)
  (let [arg-type (type-check* ctx (second expr))]
    (when-not (halite-types/subtype? arg-type (halite-types/vector-type :Value))
      (throw-err (h-err/argument-not-vector {:op 'first, :form expr})))
    (when (halite-types/empty-vectors arg-type)
      (throw-err (h-err/argument-empty {:form expr})))
    (second arg-type)))

(s/defn ^:private type-check-rest :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 1 expr)
  (let [arg-type (type-check* ctx (second expr))]
    (when-not (halite-types/subtype? arg-type (halite-types/vector-type :Value))
      (throw-err (h-err/argument-not-vector {:op 'rest, :form expr})))
    arg-type))

(s/defn ^:private type-check-conj :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (let [[base-type & elem-types] (mapv (partial type-check* ctx) (rest expr))]
    (when-not (halite-types/subtype? base-type (halite-types/coll-type :Value))
      (throw-err (h-err/argument-not-set-or-vector {:form expr})))
    (doseq [[elem elem-type] (map vector (drop 2 expr) elem-types)]
      (when (halite-types/maybe-type? elem-type)
        (throw-err (h-err/cannot-conj-unset {:type-string (symbol (halite-types/coll-type-string base-type)), :form elem}))))
    (halite-types/change-elem-type
     base-type
     (reduce halite-types/meet (halite-types/elem-type base-type) elem-types))))

(s/defn ^:private type-check-concat :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 2 expr)
  (let [op (first expr)
        [s t] (mapv (partial type-check* ctx) (rest expr))]
    (when-not (halite-types/subtype? s (halite-types/coll-type :Value))
      (throw-err (h-err/arg-type-mismatch (add-position 0 {:op op :expected-type-description (text "a set or vector"), :form expr}))))
    (when-not (halite-types/subtype? t (halite-types/coll-type :Value))
      (throw-err (h-err/arg-type-mismatch (add-position 1 {:op op :expected-type-description (text "a set or vector"), :form expr}))))
    (when (and (halite-types/subtype? s (halite-types/vector-type :Value)) (not (halite-types/subtype? t (halite-types/vector-type :Value))))
      (throw-err (h-err/not-both-vectors {:op op, :form expr})))
    (halite-types/meet s
                       (halite-types/change-elem-type s (halite-types/elem-type t)))))

(s/defn ^:private type-check-refine-to :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[subexpr kw] (rest expr)
        s (type-check* ctx subexpr)]
    (when-not (halite-types/subtype? s (halite-types/instance-type))
      (throw-err (h-err/arg-type-mismatch (add-position 0 {:op 'refine-to :expected-type-description (text "an instance"), :form expr, :actual s}))))
    (when-not (halite-types/namespaced-keyword? kw)
      (throw-err (h-err/arg-type-mismatch (add-position 1 {:op 'refine-to :expected-type-description (text "a spec id"), :form expr}))))
    (when-not (halite-envs/lookup-spec (:senv ctx) kw)
      (throw-err (h-err/resource-spec-not-found {:spec-id (symbol kw) :form expr})))
    (halite-types/concrete-spec-type kw)))

(s/defn ^:private type-check-refines-to? :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[subexpr kw] (rest expr)
        s (type-check* ctx subexpr)]
    (when-not (halite-types/subtype? s (halite-types/instance-type))
      (throw-err (h-err/arg-type-mismatch (add-position 0 {:op 'refines-to? :expected-type-description (text "an instance"), :form expr}))))
    (when-not (halite-types/namespaced-keyword? kw)
      (throw-err (h-err/arg-type-mismatch (add-position 1 {:op 'refines-to? :expected-type-description (text "a spec id"), :form expr}))))
    (when-not (halite-envs/lookup-spec (:senv ctx) kw)
      (throw-err (h-err/resource-spec-not-found {:spec-id (symbol kw) :form expr})))
    :Boolean))

(s/defn ^:private type-check-valid :- halite-types/HaliteType
  [ctx :- TypeContext, [_valid subexpr :as expr]]
  (let [t (type-check* ctx subexpr)]
    (cond
      (halite-types/spec-type? t) (halite-types/maybe-type t)
      ;; questionable...
      ;;(and (vector? t) (= :Maybe (first t)) (spec-type? (second t))) t
      :else (throw-err (h-err/arg-type-mismatch (add-position nil {:op 'valid, :expected-type-description (text "an instance of known type"), :form expr}))))))

(s/defn ^:private type-check-valid? :- halite-types/HaliteType
  [ctx :- TypeContext, [_valid? subexpr :as expr]]
  (let [t (type-check* ctx subexpr)]
    (cond
      (halite-types/spec-type? t) :Boolean
      ;; questionable...
      ;;(and (vector? t) (= :Maybe (first t)) (spec-type? (second t))) :Boolean
      :else (throw-err (h-err/arg-type-mismatch (add-position nil {:op 'valid?, :expected-type-description (text "an instance of known type"), :form expr}))))))

(s/defn ^:private type-check* :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (cond
    (boolean? expr) :Boolean
    (halite-base/integer-or-long? expr) :Integer
    (halite-base/fixed-decimal? expr) (type-check-fixed-decimal expr)
    (string? expr) :String
    (symbol? expr) (type-check-symbol ctx expr)
    (map? expr) (check-instance type-check* :form ctx expr)
    (seq? expr) (condp = (first expr)
                  'get (type-check-get ctx expr)
                  'get-in (type-check-get-in ctx expr)
                  '= (type-check-equals ctx expr)
                  'not= (type-check-equals ctx expr) ; = and not= have same typing rule
                  'rescale (type-check-set-scale ctx expr)
                  'if (type-check-if ctx expr)
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
    (coll? expr) (check-coll type-check* :form ctx expr)
    :else (throw-err (h-err/syntax-error {:form expr, :form-class (class expr)}))))

(s/defn type-check :- halite-types/HaliteType
  "Return the type of the expression, or throw an error if the form is syntactically invalid,
  or not well typed in the given typ environment."
  [senv :- (s/protocol halite-envs/SpecEnv) tenv :- (s/protocol halite-envs/TypeEnv) expr :- s/Any]
  (type-check* {:senv senv :tenv tenv} expr))

(s/defn type-check-spec
  [senv :- (s/protocol halite-envs/SpecEnv), spec-info :- halite-envs/SpecInfo]
  (let [{:keys [constraints refines-to]} spec-info
        tenv (halite-envs/type-env-from-spec senv spec-info)]
    (doseq [[cname cexpr] constraints]
      (when (not= :Boolean (type-check senv tenv cexpr))
        (throw-err (h-err/not-boolean-constraint {:expr cexpr}))))))

(def syntax-check halite-syntax-check/syntax-check)

(def integer-or-long? halite-base/integer-or-long?)

(def fixed-decimal? halite-base/fixed-decimal?)

(def check-count halite-base/check-count)

(def h< halite-eval/h<)

(def h> halite-eval/h>)

(def h<= halite-eval/h<=)

(def h>= halite-eval/h>=)

(def h+ halite-eval/h+)

(def h- halite-eval/h-)

(s/defn eval-expr :- s/Any
  "Type check a halite expression against the given type environment,
  evaluate it in the given environment, and return the result. The bindings
  in the environment are checked against the type environment before evaluation."
  [senv :- (s/protocol halite-envs/SpecEnv), tenv :- (s/protocol halite-envs/TypeEnv), env :- (s/protocol halite-envs/Env), expr]
  (type-check senv tenv expr)
  (let [declared-symbols (set (keys (halite-envs/scope tenv)))
        bound-symbols (set (keys (halite-envs/bindings env)))
        unbound-symbols (set/difference declared-symbols bound-symbols)
        empty-env (halite-envs/env {})
        ;; All runtime values are homoiconic. We eval them in an empty environment
        ;; to initialize refinements for all instances.
        env (reduce
             (fn [env [k v]]
               (halite-envs/bind env k (with-eval-bindings
                                         (halite-eval/eval-expr* {:env empty-env :senv senv} v))))
             empty-env
             (halite-envs/bindings env))]
    (when (seq unbound-symbols)
      (throw-err (h-err/symbols-not-bound {:unbound-symbols unbound-symbols, :tenv tenv, :env env})))
    (doseq [sym declared-symbols]
      (let [declared-type (get (halite-envs/scope tenv) sym)
            value (with-eval-bindings
                    (halite-eval/eval-expr* {:env empty-env :senv senv} (get (halite-envs/bindings env) sym)))
            actual-type (type-of senv tenv value)]
        (when-not (halite-types/subtype? actual-type declared-type)
          (throw-err (h-err/value-of-wrong-type {:variable sym :value value :expected declared-type :actual actual-type})))))
    (with-eval-bindings
      (halite-eval/eval-expr* {:env env :senv senv} expr))))

;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite
  "Expression language for resource spec constraints and refinements that is almost, but
  not quite, a drop-in replacement for salt."
  (:require [clojure.math.numeric-tower :refer [expt]]
            [clojure.set :as set]
            [clojure.string :as str]
            [jibe.h-err :as h-err]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.lib.fixed-decimal :as fixed-decimal]
            [jibe.lib.format-errors :as format-errors :refer [throw-err with-exception-data text]]
            [schema.core :as s])
  (:import [clojure.lang BigInt ExceptionInfo]))

(set! *warn-on-reflection* true)

(def reserved-words
  "Symbols beginning with $ that are currently defined by halite."
  '#{$no-value})

(def external-reserved-words
  "Any symbol beginning with $ may be defined in any future version of halite,
  except for symbols in this list, which halite itself promises not to use so
  that they can be safely added to environments by projects (such as jibe) that
  _use_ halite."
  '#{$this})

(declare eval-expr)
(declare eval-expr*)

(declare type-check*)

(s/defschema ^:private TypeContext {:senv (s/protocol halite-envs/SpecEnv) :tenv (s/protocol halite-envs/TypeEnv)})

(s/defschema ^:private EvalContext {:senv (s/protocol halite-envs/SpecEnv) :env (s/protocol halite-envs/Env)})

(def ^:private ^:dynamic *refinements*)

(defn integer-or-long? [value]
  (or (instance? Long value)
      (instance? Integer value)))

(defn fixed-decimal? [value]
  (fixed-decimal/fixed-decimal? value))

(s/defn ^:private eval-predicate :- Boolean
  [ctx :- EvalContext, tenv :- (s/protocol halite-envs/TypeEnv), err-msg :- String, bool-expr]
  ;; TODO: currently this with-exception-data form causes err-msg to be thrown without an error code
  (with-exception-data err-msg {:form bool-expr}
    (let [t (type-check* {:senv (:senv ctx) :tenv tenv} bool-expr)]
      (when (not= :Boolean t)
        (throw-err (h-err/not-boolean-constraint {:type t}))))
    (true? (eval-expr* ctx bool-expr))))

(s/defn ^:private eval-refinement :- (s/maybe s/Any)
  "Returns an instance of type spec-id, projected from the instance vars in ctx,
  or nil if the guards prevent this projection."
  [ctx :- EvalContext, tenv :- (s/protocol halite-envs/TypeEnv),  spec-id :- halite-types/NamespacedKeyword, expr]
  (if (contains? *refinements* spec-id)
    (*refinements* spec-id) ;; cache hit
    (let [expected-type (halite-types/maybe-type (halite-types/concrete-spec-type spec-id))
          t (type-check* {:senv (:senv ctx) :tenv tenv} expr)]
      (when-not (halite-types/subtype? t expected-type)
        (throw-err (h-err/invalid-refinement-expression {:form expr})))
      (eval-expr* ctx expr))))

(s/defn ^:private refines-to? :- Boolean
  [inst spec-type :- halite-types/HaliteType]
  (let [spec-id (halite-types/spec-id spec-type)]
    (or (= spec-id (:$type inst))
        (boolean (get (:refinements (meta inst)) spec-id)))))

(s/defn ^:private concrete? :- Boolean
  "Returns true if v is fully concrete (i.e. does not contain a value of an abstract specification), false otherwise."
  [senv :- (s/protocol halite-envs/SpecEnv), v]
  (cond
    (or (integer-or-long? v) (fixed-decimal? v) (boolean? v) (string? v)) true
    (map? v) (let [spec-id (:$type v)
                   spec-info (or (halite-envs/lookup-spec senv spec-id)
                                 (h-err/resource-spec-not-found {:spec-id (symbol spec-id)}))]
               (and (not (:abstract? spec-info))
                    (every? (partial concrete? senv) (vals (dissoc v :$type)))))
    (coll? v) (every? (partial concrete? senv) v)
    :else (throw (ex-info (format "BUG! Not a value: %s" (pr-str v)) {:value v}))))

(s/defn ^:private check-against-declared-type
  "Runtime check that v conforms to the given type, which is the type of v as declared in a resource spec.
  This function supports the semantics of abstract specs. A declared type of :foo/Bar is replaced by :Instance
  during type checking when the spec foo/Bar is abstract. The type system ensures that v is some instance,
  but at runtime, we need to confirm that v actually refines to the expected type. This function does,
  and recursively deals with collection types."
  [declared-type :- halite-types/HaliteType, v]
  (let [declared-type (halite-types/no-maybe declared-type)]
    (cond
      (halite-types/spec-id declared-type) (when-not (refines-to? v declared-type)
                                             (throw-err (h-err/no-refinement-path
                                                         {:type (symbol (:$type v))
                                                          :value v
                                                          :target-type (symbol (halite-types/spec-id declared-type))})))
      :else (if-let [elem-type (halite-types/elem-type declared-type)]
              (dorun (map (partial check-against-declared-type elem-type) v))
              nil))))

(s/defn ^:private validate-instance :- s/Any
  "Check that an instance satisfies all applicable constraints.
  Return the instance if so, throw an exception if not.
  Assumes that the instance has been type-checked successfully against the given type environment."
  [senv :- (s/protocol halite-envs/SpecEnv), inst :- s/Any]
  (let [spec-id (:$type inst)
        {:keys [spec-vars refines-to] :as spec-info} (halite-envs/lookup-spec senv spec-id)
        spec-tenv (halite-envs/type-env-from-spec senv spec-info)
        env (halite-envs/env-from-inst spec-info inst)
        ctx {:senv senv, :env env}
        satisfied? (fn [[cname expr]]
                     (eval-predicate ctx spec-tenv (format "invalid constraint '%s' of spec '%s'" cname (symbol spec-id)) expr))]

    ;; check that all variables have values that are concrete and that conform to the
    ;; types declared in the parent resource spec
    (doseq [[kw v] (dissoc inst :$type)
            :let [declared-type (->> kw spec-vars (halite-envs/halite-type-from-var-type senv))]]
      ;; TODO: consider letting instances of abstract spec contain abstract values
      (when-not (concrete? senv v)
        (throw-err (h-err/no-abstract {:value v})))
      (check-against-declared-type declared-type v))

    ;; check all constraints
    (let [violated-constraints (->> spec-info :constraints (remove satisfied?) vec)]
      (when (seq violated-constraints)
        (throw-err (h-err/invalid-instance {:spec-id (symbol spec-id)
                                            :violated-constraints (mapv (comp symbol first) violated-constraints)
                                            :value inst
                                            :halite-error :constraint-violation}))))

    ;; fully explore all active refinement paths, and store the results
    (with-meta
      inst
      {:refinements
       (->> refines-to
            (sort-by first)
            (reduce
             (fn [transitive-refinements [spec-id {:keys [expr inverted? name]}]]
               (binding [*refinements* transitive-refinements]
                 (let [inst (try
                              (with-exception-data {:refinement name}
                                (eval-refinement ctx spec-tenv spec-id expr))
                              (catch ExceptionInfo ex
                                (if (and inverted? (= :constraint-violation (:halite-error (ex-data ex))))
                                  ex
                                  (throw ex))))]
                   (cond-> transitive-refinements
                     (not= :Unset inst) (->
                                         (merge (:refinements (meta inst)))
                                         (assoc spec-id inst))))))
             {}))})))

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
    (integer-or-long? value) :Integer
    (fixed-decimal? value) (type-check-fixed-decimal value)
    (string? value) :String
    (= :Unset value) :Unset
    (map? value) (let [t (check-instance type-of* :value ctx value)]
                   (validate-instance (:senv ctx) value)
                   t)
    (coll? value) (check-coll type-of* :value ctx value)
    :else (throw-err (h-err/invalid-value {:value value}))))

(s/defn type-of :- halite-types/HaliteType
  "Return the type of the given runtime value, or throw an error if the value is invalid and cannot be typed.
  For instances, this function checks all applicable constraints. Any constraint violations result in a thrown exception."
  [senv :- (s/protocol halite-envs/SpecEnv), tenv :- (s/protocol halite-envs/TypeEnv), value :- s/Any]
  (type-of* {:senv senv :tenv tenv} value))

(s/defschema FnSignature
  {:arg-types [halite-types/HaliteType]
   (s/optional-key :variadic-tail) halite-types/HaliteType
   :return-type halite-types/HaliteType})

(s/defschema Builtin
  {:signatures (s/constrained [FnSignature] seq)
   :impl clojure.lang.IFn})

(def & :&)

(defn make-signatures [signatures]
  (vec (for [[arg-types return-type] (partition 2 signatures)
             :let [n (count arg-types)
                   variadic? (and (< 1 n) (= :& (nth arg-types (- n 2))))]]
         (cond-> {:arg-types (cond-> arg-types variadic? (subvec 0 (- n 2)))
                  :return-type return-type}
           variadic? (assoc :variadic-tail (last arg-types))))))

(s/defn ^:private mk-builtin :- Builtin
  [impl & signatures]
  (when (not= 0 (mod (count signatures) 2))
    (throw (ex-info "argument count must be a multiple of 2" {})))
  {:impl impl
   :signatures (make-signatures signatures)})

(def ^:dynamic *limits* {:string-literal-length nil
                         :string-runtime-length nil
                         :vector-literal-count nil
                         :vector-runtime-count nil
                         :set-literal-count nil
                         :set-runtime-count nil
                         :list-literal-count nil
                         :expression-nesting-depth nil})

(s/defn check-count [object-type count-limit c context]
  (when (> (count c) count-limit)
    (throw-err (h-err/size-exceeded (merge context {:object-type object-type
                                                    :actual-count (count c)
                                                    :count-limit count-limit
                                                    :value c}))))
  c)

(s/defn check-limit [limit-key v]
  (when-let [limit (get *limits* limit-key)]
    (condp = limit-key
      :string-literal-length (check-count 'String limit v {})
      :string-runtime-length (check-count 'String limit v {})
      :vector-literal-count (check-count 'Vector limit v {})
      :vector-runtime-count (check-count 'Vector limit v {})
      :set-literal-count (check-count 'Set limit v {})
      :set-runtime-count (check-count 'Set limit v {})
      :list-literal-count (check-count 'List limit v {})))
  v)

(defmacro math-f [integer-f fixed-decimal-f]
  `(fn [& args#]
     (apply (if (fixed-decimal? (first args#)) ~fixed-decimal-f ~integer-f) args#)))

(def ^:private hstr  (math-f str  fixed-decimal/string-representation))
(def ^:private hneg? (math-f neg? fixed-decimal/fneg?))
(def           h+    (math-f +    fixed-decimal/f+))
(def           h-    (math-f -    fixed-decimal/f-))
(def ^:private h*    (math-f *    fixed-decimal/f*))
(def ^:private hquot (math-f quot fixed-decimal/fquot))
(def ^:private habs  (comp #(if (hneg? %)
                              (throw-err (h-err/abs-failure {:value %}))
                              %)
                           (math-f abs  fixed-decimal/fabs)))
(def           h<=   (math-f <=   fixed-decimal/f<=))
(def           h>=   (math-f >=   fixed-decimal/f>=))
(def           h<    (math-f <    fixed-decimal/f<))
(def           h>    (math-f >    fixed-decimal/f>))

(def ^:private decimal-sigs (mapcat (fn [s]
                                      [[(halite-types/decimal-type s) (halite-types/decimal-type s) & (halite-types/decimal-type s)]
                                       (halite-types/decimal-type s)])
                                    (range 1 (inc fixed-decimal/max-scale))))

(def ^:private decimal-sigs-single (mapcat (fn [s]
                                             [[(halite-types/decimal-type s) :Integer & :Integer]
                                              (halite-types/decimal-type s)])
                                           (range 1 (inc fixed-decimal/max-scale))))

(def ^:private decimal-sigs-unary (mapcat (fn [s]
                                            [[(halite-types/decimal-type s)]
                                             (halite-types/decimal-type s)])
                                          (range 1 (inc fixed-decimal/max-scale))))

(def ^:private decimal-sigs-binary (mapcat (fn [s]
                                             [[(halite-types/decimal-type s) :Integer]
                                              (halite-types/decimal-type s)])
                                           (range 1 (inc fixed-decimal/max-scale))))

(def ^:private decimal-sigs-boolean (mapcat (fn [s]
                                              [[(halite-types/decimal-type s) (halite-types/decimal-type s)]
                                               :Boolean])
                                            (range 1 (inc fixed-decimal/max-scale))))

(def ^:private decimal-sigs-collections (mapcat (fn [s]
                                                  [[(halite-types/set-type (halite-types/decimal-type s))]
                                                   (halite-types/vector-type (halite-types/decimal-type s))
                                                   [(halite-types/vector-type (halite-types/decimal-type s))]
                                                   (halite-types/vector-type (halite-types/decimal-type s))])
                                                (range 1 (inc fixed-decimal/max-scale))))

(defn handle-overflow [f]
  (fn [& args]
    (try (apply f args)
         (catch ArithmeticException _
           (throw-err (h-err/overflow {}))))))

(def builtins
  (s/with-fn-validation
    {'+ (apply mk-builtin (handle-overflow h+) (into [[:Integer :Integer & :Integer] :Integer] decimal-sigs))
     '- (apply mk-builtin (handle-overflow h-) (into [[:Integer :Integer & :Integer] :Integer] decimal-sigs))
     '* (apply mk-builtin (handle-overflow h*) (into [[:Integer :Integer & :Integer] :Integer] decimal-sigs-single))
     '< (apply mk-builtin h< (into [[:Integer :Integer] :Boolean] decimal-sigs-boolean))
     '<= (apply mk-builtin h<= (into [[:Integer :Integer] :Boolean] decimal-sigs-boolean))
     '> (apply mk-builtin h> (into [[:Integer :Integer] :Boolean] decimal-sigs-boolean))
     '>= (apply mk-builtin h>= (into [[:Integer :Integer] :Boolean] decimal-sigs-boolean))
     'count (mk-builtin count [(halite-types/coll-type :Value)] :Integer)
     'and (mk-builtin (fn [& args] (every? true? args))
                      [:Boolean & :Boolean] :Boolean)
     'or (mk-builtin (fn [& args] (true? (some true? args)))
                     [:Boolean & :Boolean] :Boolean)
     'not (mk-builtin not [:Boolean] :Boolean)
     '=> (mk-builtin (fn [a b] (if a b true))
                     [:Boolean :Boolean] :Boolean)
     'contains? (mk-builtin contains? [(halite-types/set-type :Value) :Value] :Boolean)
     'inc (mk-builtin (handle-overflow inc) [:Integer] :Integer)
     'dec (mk-builtin (handle-overflow dec)  [:Integer] :Integer)
     'div (apply mk-builtin (fn [num divisor]
                              (when (= 0 divisor)
                                (throw-err (h-err/divide-by-zero {:num num})))
                              (hquot num divisor)) (into [[:Integer :Integer] :Integer] decimal-sigs-binary))
     'mod (mk-builtin (fn [num divisor]
                        (when (= 0 divisor)
                          (throw-err (h-err/divide-by-zero {:num num})))
                        (mod num divisor)) [:Integer :Integer] :Integer)
     'expt (mk-builtin (fn [x p]
                         (when (neg? p)
                           (throw-err (h-err/invalid-exponent {:exponent p})))
                         (let [result (expt x p)]
                           (when (instance? BigInt result)
                             (throw-err (h-err/overflow {})))
                           result))
                       [:Integer :Integer] :Integer)
     'abs (apply mk-builtin habs (into [[:Integer] :Integer] decimal-sigs-unary))
     'str (mk-builtin (comp (partial check-limit :string-runtime-length) str) [& :String] :String)
     'subset? (mk-builtin set/subset? [(halite-types/set-type :Value) (halite-types/set-type :Value)] :Boolean)
     'sort (apply mk-builtin (fn [expr]
                               ((if (and (pos? (count expr))
                                         (fixed-decimal? (first expr)))
                                  (comp vec (partial sort-by fixed-decimal/sort-key))
                                  (comp vec sort)) expr))
                  (into [[halite-types/empty-set] halite-types/empty-vector
                         [halite-types/empty-vector] halite-types/empty-vector
                         [(halite-types/set-type :Integer)] (halite-types/vector-type :Integer)
                         [(halite-types/vector-type :Integer)] (halite-types/vector-type :Integer)]
                        decimal-sigs-collections))
     'range (mk-builtin (comp vec range)
                        [:Integer :Integer :Integer] (halite-types/vector-type :Integer)
                        [:Integer :Integer] (halite-types/vector-type :Integer)
                        [:Integer] (halite-types/vector-type :Integer))
     'error (mk-builtin #(throw-err (h-err/spec-threw {:spec-error-str %}))
                        [:String] :Nothing)}))

(defn check-n [object-type n v error-context]
  (when (and n
             (> v n))
    (throw-err (h-err/limit-exceeded {:object-type object-type
                                      :value v
                                      :limit n}))))

;; for syntax checks

(def max-symbol-length 256)

(def symbol-regex
  ;; from chouser, started with edn symbol description https://github.com/edn-format/edn#symbols
  #"(?x) # allow comments and whitespace in regex
        (?: # prefix (namespace) part
          (?: # first character of prefix
              [A-Za-z*!$?=<>_.]      # begin with non-numeric
              | [+-] (?=[^0-9]))     # for +/-, second character must be non-numeric
          [0-9A-Za-z*!$?=<>_+.-]*    # subsequent characters of prefix
          /                          # prefix / name separator
        )?                           # prefix is optional
        (?: # name (suffix) part
          (?!true$) (?!false$) (?!nil$) # these are not to be read as symbols
          (?: # first character of name
              [A-Za-z*!$?=<>_.]      # begin with non-numeric
              | [+-] (?=[^0-9]|$))   # for +/-, second character must be non-numeric
          [0-9A-Za-z*!$?=<>_+.-]*)   # subsequent characters of name
        ")

(defn- check-symbol-string [expr]
  (let [s (if (symbol? expr)
            (str expr)
            (subs (str expr) 1))]
    (when-not (re-matches symbol-regex s)
      (throw-err ((if (symbol? expr)
                    h-err/invalid-symbol-char
                    h-err/invalid-keyword-char) {:form expr})))
    (when-not (<= (count s) max-symbol-length)
      (throw-err ((if (symbol? expr)
                    h-err/invalid-symbol-length
                    h-err/invalid-keyword-length) {:form expr
                                                   :length (count s)
                                                   :limit max-symbol-length})))))

(s/defn syntax-check
  ([expr]
   (syntax-check 0 expr))
  ([depth expr]
   (check-n "expression nesting" (get *limits* :expression-nesting-depth) depth {})
   (cond
     (boolean? expr) true
     (integer-or-long? expr) true
     (fixed-decimal? expr) true
     (string? expr) (do (check-limit :string-literal-length expr) true)
     (symbol? expr) (do (check-symbol-string expr) true)
     (keyword? expr) (do (check-symbol-string expr) true)

     (map? expr) (and (or (:$type expr)
                          (throw-err (h-err/missing-type-field {:expr expr})))
                      (->> expr
                           (mapcat identity)
                           (map (partial syntax-check (inc depth)))
                           dorun))
     (seq? expr) (do
                   (or (#{'=
                          'rescale
                          'any?
                          'concat
                          'conj
                          'difference
                          'every?
                          'filter
                          'first
                          'get
                          'get-in
                          'if
                          'if-value
                          'if-value-let
                          'intersection
                          'let
                          'map
                          'not=
                          'reduce
                          'refine-to
                          'refines-to?
                          'rest
                          'sort-by
                          'union
                          'valid
                          'valid?
                          'when
                          'when-value
                          'when-value-let} (first expr))
                       (get builtins (first expr))
                       (throw-err (h-err/unknown-function-or-operator {:op (first expr)
                                                                       :expr expr})))
                   (check-limit :list-literal-count expr)
                   (->> (rest expr)
                        (map (partial syntax-check (inc depth)))
                        dorun)
                   true)
     (or (vector? expr)
         (set? expr)) (do (check-limit (cond
                                         (vector? expr) :vector-literal-count
                                         (set? expr) :set-literal-count)
                                       expr)
                          (->> (map (partial syntax-check (inc depth)) expr) dorun))
     :else (throw-err (h-err/syntax-error {:form expr
                                           :form-class (class expr)})))))

(s/defn matches-signature?
  [sig :- FnSignature, actual-types :- [halite-types/HaliteType]]
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
        {:keys [signatures impl] :as builtin} (get builtins op)
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

(defn arg-count-exactly
  [n form]
  (when (not= n (count (rest form)))
    (throw-err (h-err/wrong-arg-count {:op (first form)
                                       :expected-arg-count n
                                       :actual-arg-count (count (rest form))
                                       :form form}))))

(defn arg-count-at-least
  [n form]
  (when (< (count (rest form)) n)
    (throw-err (h-err/wrong-arg-count-min {:op (first form)
                                           :minimum-arg-count n
                                           :actual-arg-count (count (rest form))
                                           :form form}))))

(defn ^:private type-check-lookup [ctx form subexpr-type index]
  (cond
    (halite-types/halite-vector-type? subexpr-type)
    (let [index-type (type-check* ctx index)]
      (when (not= :Integer index-type)
        (throw-err (h-err/invalid-vector-index {:form form :index-form index, :expected :Integer, :actual-type index-type})))
      (halite-types/elem-type subexpr-type))

    (and (halite-types/spec-type? subexpr-type)
         (halite-types/spec-id subexpr-type)
         (not (halite-types/needs-refinement? subexpr-type)))
    (let [field-types (-> (->> subexpr-type halite-types/spec-id (halite-envs/lookup-spec (:senv ctx)) :spec-vars)
                          (update-vals (partial halite-envs/halite-type-from-var-type (:senv ctx))))]
      (when-not (and (keyword? index) (halite-types/bare? index))
        (throw-err (h-err/invalid-instance-index {:form form, :index-form index})))
      (when-not (contains? field-types index)
        (throw-err (h-err/field-name-not-in-spec {:form form, :invalid-vars [(symbol index)]})))
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
  :Boolean)

(defn- add-position [n m]
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
    (when-not (integer-or-long? scale)
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
        (when-not (symbol? sym)
          (throw-err (h-err/let-symbols-required {:form expr})))
        (when (reserved-words sym)
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
      (throw-err (h-err/binding-target-must-be-symbol {:op op :form expr :sym sym})))
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
    [(first coll-type) (if (halite-types/subtype? coll-type halite-types/empty-coll)
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
      (throw-err (h-err/accumulator-target-must-be-symbol {:op op, :accumulator acc, :form expr})))
    (when-not (and (symbol? elem) (halite-types/bare? elem))
      (throw-err (h-err/element-binding-target-must-be-symbol {:op op, :form expr, :element elem})))
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
      (throw-err (h-err/if-value-must-be-symbol {:op op
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
      (throw-err (h-err/binding-target-must-be-symbol {:op op
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
    (when (= halite-types/empty-vector arg-type)
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
    (integer-or-long? expr) :Integer
    (fixed-decimal? expr) (type-check-fixed-decimal expr)
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

(declare eval-expr*)

(defn- get-from-vector [ctx expr target index-expr]
  (let [index (eval-expr* ctx index-expr)]
    (when-not (< -1 index (count target))
      (throw-err (h-err/index-out-of-bounds {:form expr
                                             :index index
                                             :length (count target)})))
    (nth target index)))

(s/defn ^:private eval-get :- s/Any
  [ctx :- EvalContext, expr]
  (let [[_ target-expr index-expr] expr
        target (eval-expr* ctx target-expr)]
    (if (vector? target)
      (get-from-vector ctx expr target index-expr)
      (get target index-expr :Unset))))

(s/defn ^:private eval-get-in :- s/Any
  [ctx :- EvalContext, expr]
  (let [[_ target-expr indexes] expr]
    (reduce (fn [target index-expr]
              (if (vector? target)
                (get-from-vector ctx expr target index-expr)
                (get target index-expr :Unset)))
            (eval-expr* ctx target-expr)
            indexes)))

(s/defn ^:private eval-let :- s/Any
  [ctx :- EvalContext, bindings body]
  (eval-expr*
   (reduce
    (fn [ctx [sym body]]
      (when (reserved-words sym)
        (throw-err (h-err/cannot-bind-reserved-word {:sym sym
                                                     :bindings bindings
                                                     :body body})))
      (update ctx :env halite-envs/bind sym (eval-expr* ctx body)))
    ctx
    (partition 2 bindings))
   body))

(s/defn ^:private eval-if-value :- s/Any
  "handles if-value and when-value"
  [ctx :- EvalContext, expr]
  (let [[sym then else] (rest expr)]
    (if (not= :Unset (eval-expr* ctx sym))
      (eval-expr* ctx then)
      (if (= 4 (count expr))
        (eval-expr* ctx else)
        :Unset))))

(s/defn ^:private eval-if-value-let :- s/Any
  [ctx :- EvalContext, expr]
  (let [[[sym maybe] then else] (rest expr)
        maybe-val (eval-expr* ctx maybe)]
    (if (not= :Unset maybe-val)
      (eval-expr* (update ctx :env halite-envs/bind sym maybe-val) then)
      (if (= 4 (count expr))
        (eval-expr* ctx else)
        :Unset))))

(s/defn ^:private eval-quantifier-bools :- [Boolean]
  [ctx :- EvalContext,
   [[sym coll] pred]]
  (mapv #(eval-expr* (update ctx :env halite-envs/bind sym %) pred)
        (eval-expr* ctx coll)))

(s/defn ^:private eval-comprehend :- [s/Any]
  [ctx :- EvalContext,
   [[sym coll] expr]]
  (let [coll-val (eval-expr* ctx coll)]
    [coll-val
     (map #(eval-expr* (update ctx :env halite-envs/bind sym %) expr) coll-val)]))

(s/defn ^:private eval-reduce :- s/Any
  [ctx :- EvalContext,
   [op [acc init] [elem coll] body]]
  (reduce (fn [a b]
            (eval-expr* (update ctx :env
                                #(-> % (halite-envs/bind acc a) (halite-envs/bind elem b)))
                        body))
          (eval-expr* ctx init)
          (eval-expr* ctx coll)))

(s/defn ^:private eval-refine-to :- s/Any
  [ctx :- EvalContext, expr]
  (let [[subexp t] (rest expr)
        inst (eval-expr* ctx subexp)
        result (cond-> inst
                 (not= t (:$type inst)) (-> meta :refinements t))]
    (cond
      (instance? Exception result) (throw-err (h-err/refinement-error {:type (symbol (:$type inst))
                                                                       :instance inst
                                                                       :underlying-error-message (.getMessage ^Exception result)
                                                                       :form expr}))
      (nil? result) (throw-err (h-err/no-refinement-path {:type (symbol (:$type inst)), :value inst, :target-type (symbol t), :form expr}))
      :else result)))

(defn ^:private check-collection-runtime-count [x]
  (check-limit (if (set? x)
                 :set-runtime-count
                 :vector-runtime-count)
               x))

(s/defn ^:private eval-expr* :- s/Any
  [ctx :- EvalContext, expr]
  (let [eval-in-env (partial eval-expr* ctx)]
    (cond
      (or (boolean? expr)
          (integer-or-long? expr)
          (string? expr)) expr
      (fixed-decimal? expr) expr
      (symbol? expr) (if (= '$no-value expr)
                       :Unset
                       (let [b (halite-envs/bindings (:env ctx))]
                         (if (contains? b expr)
                           (get b expr)
                           (throw-err (h-err/symbol-undefined {:form expr})))))
      (map? expr) (->> (dissoc expr :$type)
                       (map (fn [[k v]] [k (eval-in-env v)]))
                       (remove (fn [[k v]] (= :Unset v)))
                       (into (select-keys expr [:$type]))
                       (validate-instance (:senv ctx)))
      (seq? expr) (condp = (first expr)
                    'get (eval-get ctx expr)
                    'get-in (eval-get-in ctx expr)
                    '= (apply = (mapv eval-in-env (rest expr)))
                    'not= (apply not= (mapv eval-in-env (rest expr)))
                    'rescale (let [[_ f s] expr]
                               (fixed-decimal/set-scale (eval-in-env f) s))
                    'if (let [[pred then else] (rest expr)]
                          (eval-in-env (if (eval-in-env pred) then else)))
                    'when (let [[pred body] (rest expr)]
                            (if (eval-in-env pred)
                              (eval-in-env body)
                              :Unset))
                    'let (apply eval-let ctx (rest expr))
                    'if-value (eval-if-value ctx expr)
                    'when-value (eval-if-value ctx expr) ; eval-if-value handles when-value
                    'if-value-let (eval-if-value-let ctx expr)
                    'when-value-let (eval-if-value-let ctx expr) ; eval-if-value-let handles when-value-let
                    'union (reduce set/union (map eval-in-env (rest expr)))
                    'intersection (reduce set/intersection (map eval-in-env (rest expr)))
                    'difference (apply set/difference (map eval-in-env (rest expr)))
                    'first (or (first (eval-in-env (second expr)))
                               (throw-err (h-err/argument-empty {:form expr})))
                    'rest (let [arg (eval-in-env (second expr))]
                            (if (empty? arg) [] (subvec arg 1)))
                    'conj (check-collection-runtime-count (apply conj (map eval-in-env (rest expr))))
                    'concat (check-collection-runtime-count (apply into (map eval-in-env (rest expr))))
                    'refine-to (eval-refine-to ctx expr)
                    'refines-to? (let [[subexpr kw] (rest expr)
                                       inst (eval-in-env subexpr)]
                                   (refines-to? inst (halite-types/concrete-spec-type kw)))
                    'every? (every? identity (eval-quantifier-bools ctx (rest expr)))
                    'any? (boolean (some identity (eval-quantifier-bools ctx (rest expr))))
                    'map (let [[coll result] (eval-comprehend ctx (rest expr))]
                           (when (some #(= :Unset %) result)
                             (throw-err (h-err/must-produce-value {:form expr})))
                           (into (empty coll) result))
                    'filter (let [[coll bools] (eval-comprehend ctx (rest expr))]
                              (into (empty coll) (filter some? (map #(when %1 %2) bools coll))))
                    'valid (try
                             (eval-in-env (second expr))
                             (catch ExceptionInfo ex
                               (if (= :constraint-violation (:halite-error (ex-data ex)))
                                 :Unset
                                 (throw ex))))
                    'valid? (not= :Unset (eval-in-env (list 'valid (second expr))))
                    'sort-by (let [[coll indexes] (eval-comprehend ctx (rest expr))]
                               (when-not (= (count (set indexes))
                                            (count indexes))
                                 (throw-err (h-err/sort-value-collision {:form expr
                                                                         :coll coll})))
                               (mapv #(nth % 1) (if (and (pos? (count indexes))
                                                         (fixed-decimal/fixed-decimal? (first indexes)))
                                                  (sort-by
                                                   (comp fixed-decimal/sort-key first)
                                                   (map vector indexes coll))
                                                  (sort (map vector indexes coll)))))
                    'reduce (eval-reduce ctx expr)
                    (with-exception-data {:form expr}
                      (apply (or (:impl (get builtins (first expr)))
                                 (throw-err (h-err/unknown-function-or-operator {:op (first expr), :form expr})))
                             (mapv eval-in-env (rest expr)))))
      (vector? expr) (mapv eval-in-env expr)
      (set? expr) (set (map eval-in-env expr))
      (= :Unset expr) :Unset
      :else (throw-err (h-err/invalid-expression {:form expr})))))

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
               (halite-envs/bind env k (eval-expr* {:env empty-env :senv senv} v)))
             empty-env
             (halite-envs/bindings env))]
    (when (seq unbound-symbols)
      (throw-err (h-err/symbols-not-bound {:unbound-symbols unbound-symbols, :tenv tenv, :env env})))
    (doseq [sym declared-symbols]
      (let [declared-type (get (halite-envs/scope tenv) sym)
            value (eval-expr* {:env empty-env :senv senv} (get (halite-envs/bindings env) sym))
            actual-type (type-of senv tenv value)]
        (when-not (halite-types/subtype? actual-type declared-type)
          (throw-err (h-err/value-of-wrong-type {:variable sym :value value :expected declared-type :actual actual-type})))))
    (eval-expr* {:env env :senv senv} expr)))

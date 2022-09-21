;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite-eval
  "Evaluator for halite"
  (:require [clojure.math.numeric-tower :refer [expt]]
            [clojure.set :as set]
            [jibe.h-err :as h-err]
            [jibe.halite-base :as halite-base]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.lib.fixed-decimal :as fixed-decimal]
            [jibe.lib.format-errors :refer [throw-err with-exception-data]]
            [schema.core :as s])
  (:import [clojure.lang BigInt ExceptionInfo]))

(set! *warn-on-reflection* true)

;;

(def & :&)

(s/defn ^:private mk-builtin :- halite-base/Builtin
  [impl & signatures]
  (when (not= 0 (mod (count signatures) 2))
    (throw (ex-info "argument count must be a multiple of 2" {})))
  {:impl impl
   :signatures (halite-base/make-signatures signatures)})

(defmacro math-f [integer-f fixed-decimal-f]
  `(fn [& args#]
     (apply (if (halite-base/fixed-decimal? (first args#)) ~fixed-decimal-f ~integer-f) args#)))

(def ^:private hstr  (math-f str  fixed-decimal/string-representation))
(def ^:private hneg? (math-f neg? fixed-decimal/fneg?))
(def           h+    (math-f +    fixed-decimal/f+))
(def           h-    (math-f -    fixed-decimal/f-))
(def ^:private h*    (math-f *    fixed-decimal/f*))
(def ^:private hquot (math-f quot fixed-decimal/fquot))
(def ^:private habs  (comp #(if (hneg? %)
                              (throw-err (h-err/abs-failure {:value %}))
                              %)
                           (math-f abs #(try (fixed-decimal/fabs %)
                                             (catch NumberFormatException ex
                                               (throw-err (h-err/abs-failure {:value %})))))))
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
     'str (mk-builtin (comp (partial halite-base/check-limit :string-runtime-length) str) [& :String] :String)
     'subset? (mk-builtin set/subset? [(halite-types/set-type :Value) (halite-types/set-type :Value)] :Boolean)
     'sort (apply mk-builtin (fn [expr]
                               ((if (and (pos? (count expr))
                                         (halite-base/fixed-decimal? (first expr)))
                                  (comp vec (partial sort-by fixed-decimal/sort-key))
                                  (comp vec sort)) expr))
                  (into [[halite-types/empty-set] halite-types/empty-vector
                         [halite-types/empty-vector] halite-types/empty-vector
                         [(halite-types/set-type :Integer)] (halite-types/vector-type :Integer)
                         [(halite-types/vector-type :Integer)] (halite-types/vector-type :Integer)]
                        decimal-sigs-collections))
     'range (mk-builtin (comp (partial halite-base/check-limit :vector-runtime-count) vec range)
                        [:Integer :Integer :Integer] (halite-types/vector-type :Integer)
                        [:Integer :Integer] (halite-types/vector-type :Integer)
                        [:Integer] (halite-types/vector-type :Integer))
     'error (mk-builtin #(throw-err (h-err/spec-threw {:spec-error-str %}))
                        [:String] :Nothing)}))

(assert (= halite-base/builtin-symbols (set (keys builtins))))

;;

(declare eval-expr*)

(s/defschema EvalContext {:senv (s/protocol halite-envs/SpecEnv) :env (s/protocol halite-envs/Env)})

(s/defn eval-predicate :- Boolean
  [ctx :- EvalContext
   tenv :- (s/protocol halite-envs/TypeEnv)
   bool-expr]
  (with-exception-data {:form bool-expr}
    (true? (eval-expr* ctx bool-expr))))

(s/defn eval-refinement :- (s/maybe s/Any)
  "Returns an instance of type spec-id, projected from the instance vars in ctx,
  or nil if the guards prevent this projection."
  [ctx :- EvalContext
   tenv :- (s/protocol halite-envs/TypeEnv)
   spec-id :- halite-types/NamespacedKeyword
   expr]
  (eval-expr* ctx expr))

(def ^:dynamic *eval-predicate-fn* eval-predicate) ;; short-term, until we remove the dependency from evaluator to type checker

(def ^:dynamic *eval-refinement-fn* eval-refinement) ;; short-term, until we remove the dependency from evaluator to type checker

(s/defn ^:private concrete? :- Boolean
  "Returns true if v is fully concrete (i.e. does not contain a value of an abstract specification), false otherwise."
  [senv :- (s/protocol halite-envs/SpecEnv), v]
  (cond
    (or (halite-base/integer-or-long? v) (halite-base/fixed-decimal? v) (boolean? v) (string? v)) true
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
      (halite-types/spec-id declared-type) (when-not (halite-base/refines-to? v declared-type)
                                             (throw-err (h-err/no-refinement-path
                                                         {:type (symbol (:$type v))
                                                          :value v
                                                          :target-type (symbol (halite-types/spec-id declared-type))})))
      :else (if-let [elem-type (halite-types/elem-type declared-type)]
              (dorun (map (partial check-against-declared-type elem-type) v))
              nil))))

(def ^:dynamic *refinements*)

(s/defn validate-instance :- s/Any
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
                     (*eval-predicate-fn* ctx spec-tenv expr))]

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
                                (*eval-refinement-fn* ctx spec-tenv spec-id expr))
                              (catch ExceptionInfo ex
                                (if (and inverted? (= :constraint-violation (:halite-error (ex-data ex))))
                                  ex
                                  (throw ex))))]
                   (cond-> transitive-refinements
                     (not= :Unset inst) (->
                                         (merge (:refinements (meta inst)))
                                         (assoc spec-id inst))))))
             {}))})))

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
      (when (halite-base/reserved-words sym)
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
  (halite-base/check-limit (if (set? x)
                             :set-runtime-count
                             :vector-runtime-count)
                           x))

(s/defn eval-expr* :- s/Any
  [ctx :- EvalContext, expr]
  (let [eval-in-env (partial eval-expr* ctx)]
    (cond
      (or (boolean? expr)
          (halite-base/integer-or-long? expr)
          (string? expr)) expr
      (halite-base/fixed-decimal? expr) expr
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
                                   (halite-base/refines-to? inst (halite-types/concrete-spec-type kw)))
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

;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.eval
  "Evaluator for halite"
  (:require [clojure.math.numeric-tower :refer [expt]]
            [clojure.set :as set]
            [clojure.string :as string]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.h-err :as h-err]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.lib.format-errors :refer [throw-err with-exception-data]]
            [com.viasat.halite.types :as types]
            [schema.core :as s])
  (:import [clojure.lang BigInt ExceptionInfo]))

(set! *warn-on-reflection* true)

;;

(def & :&)

(s/defn ^:private mk-builtin :- base/Builtin
  [impl & signatures]
  (when (not= 0 (mod (count signatures) 2))
    (throw (ex-info "argument count must be a multiple of 2" {})))
  {:impl impl
   :signatures (base/make-signatures signatures)})

(def ^:private decimal-sigs (mapcat (fn [s]
                                      [[(types/decimal-type s) (types/decimal-type s) & (types/decimal-type s)]
                                       (types/decimal-type s)])
                                    (range 1 (inc fixed-decimal/max-scale))))

(def ^:private decimal-sigs-single (mapcat (fn [s]
                                             [[(types/decimal-type s) :Integer & :Integer]
                                              (types/decimal-type s)])
                                           (range 1 (inc fixed-decimal/max-scale))))

(def ^:private decimal-sigs-unary (mapcat (fn [s]
                                            [[(types/decimal-type s)]
                                             (types/decimal-type s)])
                                          (range 1 (inc fixed-decimal/max-scale))))

(def ^:private decimal-sigs-binary (mapcat (fn [s]
                                             [[(types/decimal-type s) :Integer]
                                              (types/decimal-type s)])
                                           (range 1 (inc fixed-decimal/max-scale))))

(def ^:private decimal-sigs-boolean (mapcat (fn [s]
                                              [[(types/decimal-type s) (types/decimal-type s)]
                                               :Boolean])
                                            (range 1 (inc fixed-decimal/max-scale))))

(def ^:private decimal-sigs-collections (mapcat (fn [s]
                                                  [[(types/set-type (types/decimal-type s))]
                                                   (types/vector-type (types/decimal-type s))
                                                   [(types/vector-type (types/decimal-type s))]
                                                   (types/vector-type (types/decimal-type s))])
                                                (range 1 (inc fixed-decimal/max-scale))))

(defn handle-overflow [f]
  (fn [& args]
    (try (apply f args)
         (catch ArithmeticException _
           (throw-err (h-err/overflow {}))))))

(def builtins
  (s/with-fn-validation
    {'+ (apply mk-builtin (handle-overflow base/h+) (into [[:Integer :Integer & :Integer] :Integer] decimal-sigs))
     '- (apply mk-builtin (handle-overflow base/h-) (into [[:Integer :Integer & :Integer] :Integer] decimal-sigs))
     '* (apply mk-builtin (handle-overflow base/h*) (into [[:Integer :Integer & :Integer] :Integer] decimal-sigs-single))
     '< (apply mk-builtin base/h< (into [[:Integer :Integer] :Boolean] decimal-sigs-boolean))
     '<= (apply mk-builtin base/h<= (into [[:Integer :Integer] :Boolean] decimal-sigs-boolean))
     '> (apply mk-builtin base/h> (into [[:Integer :Integer] :Boolean] decimal-sigs-boolean))
     '>= (apply mk-builtin base/h>= (into [[:Integer :Integer] :Boolean] decimal-sigs-boolean))
     'count (mk-builtin count [(types/coll-type :Value)] :Integer)
     'and (mk-builtin (fn [& args] (every? true? args))
                      [:Boolean & :Boolean] :Boolean)
     'or (mk-builtin (fn [& args] (true? (some true? args)))
                     [:Boolean & :Boolean] :Boolean)
     'not (mk-builtin not [:Boolean] :Boolean)
     '=> (mk-builtin (fn [a b] (if a b true))
                     [:Boolean :Boolean] :Boolean)
     'contains? (mk-builtin contains? [(types/set-type :Value) :Value] :Boolean)
     'inc (mk-builtin (handle-overflow inc) [:Integer] :Integer)
     'dec (mk-builtin (handle-overflow dec)  [:Integer] :Integer)
     'div (apply mk-builtin (fn [num divisor]
                              (when (= 0 divisor)
                                (throw-err (h-err/divide-by-zero {:num num})))
                              (base/hquot num divisor)) (into [[:Integer :Integer] :Integer] decimal-sigs-binary))
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
     'abs (apply mk-builtin base/habs (into [[:Integer] :Integer] decimal-sigs-unary))
     'str (mk-builtin (comp (partial base/check-limit :string-runtime-length) str) [& :String] :String)
     'subset? (mk-builtin set/subset? [(types/set-type :Value) (types/set-type :Value)] :Boolean)
     'sort (apply mk-builtin (fn [expr]
                               ((if (and (pos? (count expr))
                                         (base/fixed-decimal? (first expr)))
                                  (comp vec (partial sort-by fixed-decimal/sort-key))
                                  (comp vec sort)) expr))
                  (into [[types/empty-set] types/empty-vector
                         [types/empty-vector] types/empty-vector
                         [(types/set-type :Integer)] (types/vector-type :Integer)
                         [(types/vector-type :Integer)] (types/vector-type :Integer)]
                        decimal-sigs-collections))
     'range (mk-builtin (comp (partial base/check-limit :vector-runtime-count) vec range)
                        [:Integer :Integer :Integer] (types/vector-type :Integer)
                        [:Integer :Integer] (types/vector-type :Integer)
                        [:Integer] (types/vector-type :Integer))
     'error (mk-builtin #(throw-err (h-err/spec-threw {:spec-error-str %}))
                        [:String] :Nothing)}))

(assert (= base/builtin-symbols (set (keys builtins))))

;;

(declare eval-expr*)

(s/defschema EvalContext {:senv (s/protocol envs/SpecEnv) :env (s/protocol envs/Env)})

(s/defn eval-predicate :- Boolean
  [ctx :- EvalContext
   tenv :- (s/protocol envs/TypeEnv)
   bool-expr
   spec-id :- types/NamespacedKeyword
   constraint-name :- (s/maybe base/ConstraintName)]
  (with-exception-data {:form bool-expr
                        :constraint-name constraint-name
                        :spec-id spec-id}
    (true? (eval-expr* ctx bool-expr))))

(s/defn eval-refinement :- (s/maybe s/Any)
  "Returns an instance of type spec-id, projected from the instance vars in ctx,
  or nil if the guards prevent this projection."
  [ctx :- EvalContext
   tenv :- (s/protocol envs/TypeEnv)
   spec-id :- types/NamespacedKeyword
   expr
   refinement-name :- (s/maybe String)]
  (with-exception-data {:form expr
                        :spec-id spec-id
                        :refinement refinement-name}
    (eval-expr* ctx expr)))

(def ^:dynamic *eval-predicate-fn* eval-predicate) ;; short-term, until we remove the dependency from evaluator to type checker

(def ^:dynamic *eval-refinement-fn* eval-refinement) ;; short-term, until we remove the dependency from evaluator to type checker

(s/defn ^:private concrete? :- Boolean
  "Returns true if v is fully concrete (i.e. does not contain a value of an abstract specification), false otherwise."
  [senv :- (s/protocol envs/SpecEnv), v]
  (cond
    (or (base/integer-or-long? v) (base/fixed-decimal? v) (boolean? v) (string? v)) true
    (map? v) (let [spec-id (:$type v)
                   spec-info (or (envs/lookup-spec senv spec-id)
                                 (h-err/resource-spec-not-found {:spec-id (symbol spec-id)}))]
               (and (not (:abstract? spec-info))
                    (every? (partial concrete? senv) (vals (dissoc v :$type)))))
    (coll? v) (every? (partial concrete? senv) v)
    :else (throw (ex-info (format "BUG! Not a value: %s" (pr-str v)) {:value v}))))

(declare refines-to?)

(s/defn ^:private check-against-declared-type
  "Runtime check that v conforms to the given type, which is the type of v as declared in a resource spec.
  This function supports the semantics of abstract specs. A declared type of :foo/Bar is replaced by :Instance
  during type checking when the spec foo/Bar is abstract. The type system ensures that v is some instance,
  but at runtime, we need to confirm that v actually refines to the expected type. This function does,
  and recursively deals with collection types."
  [declared-type :- types/HaliteType, v]
  ;; TODO: could this just call refine-to?
  (let [declared-type (types/no-maybe declared-type)]
    (cond
      (types/spec-id declared-type) (when-not (refines-to? v declared-type)
                                      (throw-err (h-err/no-refinement-path
                                                  {:type (symbol (:$type v))
                                                   :value v
                                                   :target-type (symbol (types/spec-id declared-type))})))
      :else (if-let [elem-type (types/elem-type declared-type)]
              (dorun (map (partial check-against-declared-type elem-type) v))
              nil))))

(def ^:dynamic *refinements*)

(def ^:dynamic *instance-path-atom* nil)

(defn remove-vals-from-map
  "Remove entries from the map if applying f to the value produced false."
  [m f]
  (->> m
       (remove (comp f second))
       (apply concat)
       (apply hash-map)))

(defn no-empty
  "Convert empty collection to nil"
  [coll]
  (when (seq coll)
    coll))

(defn no-nil
  "Remove all nil values from the map"
  [m]
  (remove-vals-from-map m nil?))

(s/defschema ConstraintResult {:constraint (assoc (dissoc envs/Constraint :expr)
                                                  :spec-id s/Keyword
                                                  (s/optional-key :expr) s/Any)
                               :result s/Any})

(s/defschema NetConstraintResults {:category (s/enum :constraint :refinement)
                                   :detail [ConstraintResult]})

(defn constraint-name-to-string [constraint]
  (let [{cname :name :keys [spec-id]} constraint]
    (str (namespace spec-id) "/" (name spec-id) "/" cname)))

(defn constraint-to-ex-data-form [constraint]
  (select-keys constraint [:spec-id :name]))

(s/defn validate-instance :- s/Any
  "Check that an instance satisfies all applicable constraints.
  Return the instance if so, throw an exception if not.
  Assumes that the instance has been type-checked successfully against the given type environment."
  [senv :- (s/protocol envs/SpecEnv)
   inst :- s/Any]
  (binding [*instance-path-atom* (if *instance-path-atom*
                                   *instance-path-atom*
                                   (atom '()))]
    (let [spec-id (:$type inst)
          _ (if (contains? (set @*instance-path-atom*) spec-id)
              (throw-err (h-err/spec-cycle-runtime {:spec-id-path (->> (conj @*instance-path-atom* spec-id)
                                                                       (mapv symbol))}))
              (swap! *instance-path-atom* conj spec-id))
          spec-id-0 spec-id
          {:keys [spec-vars refines-to] :as spec-info} (envs/lookup-spec senv spec-id)
          spec-tenv (envs/type-env-from-spec spec-info)
          env (envs/env-from-inst spec-info inst)
          ctx {:senv senv, :env env}
          constraint-f (fn [{cname :name :keys [expr] :as constraint}]
                         {:constraint constraint
                          :result (try
                                    (boolean (*eval-predicate-fn* ctx spec-tenv expr spec-id cname))
                                    (catch ExceptionInfo ex
                                      ;; handle this as data so that it produces all errors, not just the "first" one
                                      ex))})]

      ;; check that all variables have values that are concrete and that conform to the
      ;; types declared in the parent resource spec
      (doseq [[kw v] (dissoc inst :$type)
              :let [declared-type (kw spec-vars)]]
        ;; TODO: consider letting instances of abstract spec contain abstract values
        (when-not (concrete? senv v)
          (throw-err (h-err/no-abstract {:value v})))
        (check-against-declared-type declared-type v))

      ;; check all constraints
      (let [constraint-results {:category :constraint
                                :detail (->> spec-info
                                             :constraints
                                             (map (fn [[cname expr]]
                                                    {:spec-id spec-id
                                                     :name cname
                                                     :expr expr}))
                                             ;; produce constraint results in consistent order regardless of
                                             ;; order the constraints are defined in
                                             (sort-by :name)
                                             (mapv constraint-f)
                                             (remove #(= (:result %) true)))}]

        ;; fully explore all active refinement paths, and store the results
        (let [{:keys [transitive-refinements exs]}
              (->> refines-to
                   (sort-by first)
                   (reduce
                    (fn [{:keys [transitive-refinements exs]} [refines-to-spec-id {rname :name :keys [expr inverted?]} :as r]]
                      (binding [*refinements* transitive-refinements]
                        (let [{:keys [inst ex]} (try
                                                  {:inst (*eval-refinement-fn* ctx
                                                                               spec-tenv
                                                                               refines-to-spec-id
                                                                               expr
                                                                               rname)}
                                                  (catch ExceptionInfo ex
                                                    (if inverted?
                                                      {:inst ex}
                                                      {:ex {:constraint {:spec-id (if inverted?
                                                                                    refines-to-spec-id
                                                                                    spec-id)
                                                                         :name (if rname
                                                                                              ;; the rname has the spec qualifier on it already
                                                                                 (name (symbol (name (symbol rname))))
                                                                                 (str "<refine-to-" refines-to-spec-id ">"))
                                                                         :expr expr}
                                                            :result ex}})))]
                          (when (not= :Unset inst)
                            (when (not (empty? (set/intersection (set (keys transitive-refinements))
                                                                 (conj (set (keys (:refinements (meta inst)))) refines-to-spec-id))))
                              (throw-err (h-err/refinement-diamond {:spec-id (symbol spec-id-0)
                                                                    :current-refinements (mapv symbol (keys transitive-refinements))
                                                                    :additional-refinements (mapv symbol (keys (:refinements (meta inst))))
                                                                    :referred-spec-id (symbol refines-to-spec-id)}))))
                          {:transitive-refinements (cond-> transitive-refinements
                                                     (not= :Unset inst) (->
                                                                         (merge (:refinements (meta inst)))
                                                                         (assoc refines-to-spec-id inst)))
                           :exs (if ex
                                  (conj exs ex)
                                  exs)})))
                    {:transitive-refinements {}
                     :exs []}))
              refinement-results {:category :refinement
                                  :detail exs}
              reduced-results (->> [constraint-results refinement-results]
                                   (reduce (fn [reduced-results {:keys [category detail]}]
                                             (update reduced-results category into (or detail [])))
                                           {:constraint []
                                            :refinement []}))
              reduced-results (update-vals reduced-results #(sort-by (comp (fn [{:keys [spec-id name]}]
                                                                             [spec-id name]) :constraint) %))
              violated-constraints (filter (comp #(or (boolean? %)
                                                      (and (instance? ExceptionInfo %)
                                                           (= :constraint-violation (:halite-error (ex-data %)))))
                                                 :result) (reduce into []
                                                                  [(:constraint reduced-results)
                                                                   (filter (comp #(instance? Throwable %) :result)
                                                                           (:refinement reduced-results))]))
              error-constraints (filter (comp #(and (instance? Throwable %)
                                                    (not (and (instance? ExceptionInfo %)
                                                              (= :constraint-violation (:halite-error (ex-data %))))))
                                              :result) (reduce into []
                                                               [(:constraint reduced-results)
                                                                (:refinement reduced-results)]))]
          (when (and (empty? violated-constraints)
                     (= 1 (count error-constraints)))
            (throw (:result (first error-constraints))))
          (when (or (seq violated-constraints)
                    (seq error-constraints))
            (throw-err ((if (and (seq violated-constraints)
                                 (not (seq error-constraints)))
                          h-err/invalid-instance
                          h-err/spec-threw)
                        (let [constraint-errors (no-empty (mapv :result error-constraints))
                              violated-constraints-for-ex-data (->> violated-constraints
                                                                    (mapcat #(let [{:keys [constraint result]} %]
                                                                               (if (and (instance? ExceptionInfo result)
                                                                                        (= :constraint-violation (:halite-error (ex-data result))))
                                                                                 (:violated-constraints (ex-data result))
                                                                                 [(constraint-to-ex-data-form constraint)])))
                                                                    (sort-by (fn [{:keys [spec-id constraint-name]}]
                                                                               [spec-id constraint-name]))
                                                                    vec
                                                                    no-empty)]
                          (no-nil {:spec-id (symbol spec-id)
                                   :violated-constraints violated-constraints-for-ex-data
                                   :violated-constraint-labels (->> violated-constraints-for-ex-data
                                                                    (mapv constraint-name-to-string)
                                                                    no-empty)
                                   :error-constraints (->> error-constraints
                                                           (map (comp constraint-to-ex-data-form :constraint))
                                                           (sort-by (fn [{:keys [spec-id constraint-name]}]
                                                                      [spec-id constraint-name]))
                                                           vec
                                                           no-empty)
                                   :constraint-errors constraint-errors
                                   :constraint-error-strs (no-empty (mapv (comp #(or (:spec-error-str %) (:message-template %)) ex-data) constraint-errors))
                                   :spec-error-str (when (not (and (seq violated-constraints)
                                                                   (not (seq error-constraints))))
                                                     (string/join "; "
                                                                  (no-empty (mapv (comp #(or (:spec-error-str %) (:message-template %)) ex-data) constraint-errors))))
                                   :value inst
                                   :halite-error (when (and (seq violated-constraints)
                                                            (not (seq error-constraints)))
                                                   :constraint-violation)})))))
          (swap! *instance-path-atom* rest)
          (with-meta
            inst
            {:refinements transitive-refinements}))))))

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
      (when (base/reserved-words sym)
        (throw-err (h-err/cannot-bind-reserved-word {:sym sym
                                                     :bindings bindings
                                                     :body body})))
      (update ctx :env envs/bind sym (eval-expr* ctx body)))
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
      (eval-expr* (update ctx :env envs/bind sym maybe-val) then)
      (if (= 4 (count expr))
        (eval-expr* ctx else)
        :Unset))))

(s/defn ^:private eval-quantifier-bools :- [Boolean]
  [ctx :- EvalContext,
   [[sym coll] pred]]
  (mapv #(eval-expr* (update ctx :env envs/bind sym %) pred)
        (eval-expr* ctx coll)))

(s/defn ^:private eval-comprehend :- [s/Any]
  [ctx :- EvalContext,
   [[sym coll] expr]]
  (let [coll-val (eval-expr* ctx coll)]
    [coll-val
     (map #(eval-expr* (update ctx :env envs/bind sym %) expr) coll-val)]))

(s/defn ^:private eval-reduce :- s/Any
  [ctx :- EvalContext,
   [op [acc init] [elem coll] body]]
  (reduce (fn [a b]
            (eval-expr* (update ctx :env
                                #(-> % (envs/bind acc a) (envs/bind elem b)))
                        body))
          (eval-expr* ctx init)
          (eval-expr* ctx coll)))

(defn- make-refinement-error
  [inst expr result]
  (no-nil {:type (symbol (:$type inst))
           :instance inst
           :underlying-error-message (.getMessage ^Exception result)
           :form expr}))

(s/defn ^:private eval-refine-to :- s/Any
  [ctx :- EvalContext, expr]
  (let [[subexp t] (rest expr)
        inst (eval-expr* ctx subexp)
        result (cond-> inst
                 (not= t (:$type inst)) (-> meta :refinements t))]
    (cond
      (instance? Exception result) (throw-err (h-err/refinement-error (make-refinement-error inst expr result)))
      (nil? result) (throw-err (h-err/no-refinement-path {:type (symbol (:$type inst)), :value inst, :target-type (symbol t), :form expr}))
      :else result)))

(s/defn ^:private refines-to? :- Boolean
  [inst
   spec-type :- types/HaliteType]
  (let [t (types/spec-id spec-type)
        result (cond-> inst
                 (not= t (:$type inst)) (-> meta :refinements t))]
    (cond
      (instance? Exception result) (throw-err (h-err/refinement-error (make-refinement-error inst nil result)))
      (nil? result) false
      :else true)))

(defn ^:private check-collection-runtime-count [x]
  (base/check-limit (if (set? x)
                      :set-runtime-count
                      :vector-runtime-count)
                    x))

(s/defn eval-expr* :- s/Any
  [ctx :- EvalContext, expr]
  (let [eval-in-env (partial eval-expr* ctx)]
    (cond
      (or (boolean? expr)
          (base/integer-or-long? expr)
          (string? expr)) expr
      (base/fixed-decimal? expr) expr
      (symbol? expr) (if (= '$no-value expr)
                       :Unset
                       (let [b (envs/bindings (:env ctx))]
                         (if (contains? b expr)
                           (get b expr)
                           (throw-err (h-err/symbol-undefined {:form expr})))))
      (map? expr) (with-exception-data {:form expr}
                    (->> (dissoc expr :$type)
                         (map (fn [[k v]] [k (eval-in-env v)]))
                         (remove (fn [[k v]] (= :Unset v)))
                         (into (select-keys expr [:$type]))
                         (validate-instance (:senv ctx))))
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
                                   (with-exception-data {:form expr}
                                     (refines-to? inst (types/concrete-spec-type kw))))
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

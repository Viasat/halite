;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.halite-types
  "Schemata defining the set of halite type terms,
  together with functions that define the subtype
  relation and compute meets and joins."
  (:require [clojure.set :as set]
            [schema.core :as s]
            [schema.core :as schema]))

(set! *warn-on-reflection* true)

(def subtypes
  (let [m (fn [x] [:Maybe x])
        T 'T
        KW 'KW
        R2 'R2
        tv list]
    #{(tv :Nothing #{(tv :Integer :Object :Any)
                     (tv :String :Object)
                     (tv :Boolean :Object)
                     (tv [:Instance KW R2] :Object)
                     (tv [:Set T] [:Coll T] :Object)
                     (tv [:Vec T] [:Coll T])})
      (tv :Nothing :Unset #{(tv (m :Integer) :Any)
                            (tv (m :String) :Any)
                            (tv (m :Boolean) :Any)
                            (tv (m [:Instance KW R2]) :Any)
                            (tv (m [:Set T]) (m [:Coll T]) :Any)
                            (tv (m [:Vec T]) (m [:Coll T]))})
      (tv [:Instance KW R2] (m [:Instance KW R2]))
      (tv :Integer      (m :Integer))
      (tv :String       (m :String))
      (tv :Boolean      (m :Boolean))
      (tv [:Set T]      (m [:Set T]))
      (tv [:Vec T]      (m [:Vec T]))
      (tv [:Coll T]     (m [:Coll T]))}))

(defn get-edges [s]
  (if (= 2 (count s))
    (let [[mom kids] s]
      (if (set? kids)
        (mapcat (fn [kid]
                  (if (seq? kid)
                    (get-edges (cons mom kid))
                    [(list mom kid)]))
                kids)
        [s]))
    (let [es (get-edges (rest s))]
      (cons (take 2 s) es))))

(def *edges (delay (mapcat get-edges subtypes)))

(defn edges-dot [es]
  (str
   "digraph Types {\n"
   "rankdir=LR\n"
   "node [color=\"white\"]\n"
   (apply str (map (fn [[x y]]
                     (str (pr-str (pr-str x))
                          " -> "
                          (pr-str (pr-str y))
                          ";\n"))
                   (set es)))
   "}\n"))

(defn type-ptn [t]
  (let [[maybe? t] (if (and (vector? t) (= :Maybe (first t)))
                     [true (second t)]
                     [false t])
        [kind arg r2] (cond
                        (vector? t) t
                        :else [t nil nil])]
    {:maybe? maybe? :kind kind :arg arg :r2 r2}))

(defn ptn-type [{:keys [maybe? kind arg r2]}]
  (let [v (if (#{:Set :Vec :Coll :Instance} kind)
            (vec (remove nil? [kind arg r2]))
            kind)]
    (if maybe? [:Maybe v] v)))

#_(defn subtypes-svg []
    (spit "types.dot" (edges-dot @*edges))
    (clojure.java.shell/sh "dot" "-Tpng" "-O" "types.dot"))

;;;;

(s/defn bare? :- s/Bool
  "true if the symbol or keyword lacks a namespace component, false otherwise"
  [sym-or-kw :- (s/cond-pre s/Keyword s/Symbol)] (nil? (namespace sym-or-kw)))

(def namespaced?
  "true if the symbol or keyword has a namespace component, false otherwise"
  (complement bare?))

(s/defschema BareKeyword (s/constrained s/Keyword bare?))
(s/defschema NamespacedKeyword (s/constrained s/Keyword namespaced?))
(s/defschema BareSymbol (s/constrained s/Symbol bare?))

(s/defn namespaced-keyword? [kw]
  (and (keyword? kw) (namespaced? kw)))

(s/defn spec-type? :- s/Bool
  "True if t is a type structure representing a spec type, false otherwise"
  [t]
  (and (vector? t)
       (= :Instance (first t))
       (and (not= :* (second t)))))

(s/defschema TypeAtom
  "Type atoms are always unqualified keywords."
  (s/enum :Integer :String :Boolean :Object :Nothing :Any :Unset))

(declare maybe-type?)

(s/defschema InnerType
  (s/conditional
   #(and (vector? %) (= :Instance (first %))) [(s/one (s/eq :Instance) "instance-keyword")
                                               (s/one (s/cond-pre (s/eq :*)
                                                                  NamespacedKeyword)
                                                      "instance-type")
                                               (s/optional #{NamespacedKeyword} "refines-to-set")]
   vector? [(s/one (s/enum :Set :Vec :Coll) "coll-kind")
            (s/one (s/recursive #'InnerType) "elem-type")]
   :else TypeAtom))

(s/defschema HaliteType
  "A Halite type is either a type atom (keyword), or a 'type constructor' represented
  as a tuple whose first element is the constructor name, and whose remaining elements
  are types.

  Note that the :Set and :Vec constructors do not accept :Maybe types."
  (s/conditional
   #(and (vector? %) (= :Maybe (first %))) [(s/one (s/eq :Maybe) "maybe-keyword")
                                            (s/one InnerType "inner-type")]
   :else InnerType))

(s/defn ^:private strict-maybe-type? :- s/Bool
  [t :- HaliteType]
  (and (vector? t) (= :Maybe (first t))))

(s/defn maybe-type? :- s/Bool
  "True if t is :Unset or [:Maybe T], false otherwise."
  [t :- HaliteType]
  (or (= :Unset t)
      (strict-maybe-type? t)))

;;;;

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
  (when-not (empty? coll)
    coll))

(defn no-nil
  "Remove all nil values from the map"
  [m]
  (remove-vals-from-map m nil?))

;;

(defn- all-r2 [t]
  (cond
    (= :* (:arg t)) (:r2 t)
    :else (conj (or (:r2 t) #{}) (:arg t))))

(defn- remove-redundant-r2 [t]
  (if (contains? (or (:r2 t) #{}) (:arg t))
    (no-nil (assoc t :r2 (no-empty (disj (:r2 t) (:arg t)))))
    t))

(defn- instance-subtype? [s t]
  (and (or (= (:arg s) (:arg t))
           (= :* (:arg t)))
       (or (= (:maybe? s) (:maybe? t))
           (:maybe? t))))

(defn- instance-meet [s t]
  (remove-redundant-r2
   (no-nil {:maybe? (or (:maybe? s) (:maybe? t))
            :kind :Instance
            :arg (if (= (:arg s) (:arg t))
                   (:arg s)
                   :*)
            :r2 (no-empty (set/intersection (all-r2 s) (all-r2 t)))})))

(defn- instance-join [s t]
  (remove-redundant-r2 (cond
                         (or (= (:arg s) (:arg t))
                             (= :* (:arg s))
                             (= :* (:arg t)))
                         (no-nil {:maybe? (and (:maybe? s) (:maybe? t))
                                  :kind :Instance
                                  :arg (cond
                                         (= (:arg s) (:arg t)) (:arg s)
                                         (= :* (:arg s)) (:arg t)
                                         (= :* (:arg t)) (:arg s))
                                  :r2 (no-empty (set/union (all-r2 s) (all-r2 t)))})

                         :else
                         (if (and (:maybe? s) (:maybe? t))
                           {:kind :Unset}
                           {:kind :Nothing}))))

(defn- spec-id-ptn [s]
  (when (and (= :Instance (:kind s))
             (= 1 (count (all-r2 s))))
    (if (= :* (:arg s))
      (first (:r2 s))
      (:arg s))))

(s/defn spec-id :- (s/maybe NamespacedKeyword)
  [s :- HaliteType]
  (spec-id-ptn (type-ptn s)))

(defn- needs-refinement?-ptn
  [s]
  (and (= :Instance (:kind s))
       (= :* (:arg s))))

(s/defn needs-refinement?
  [s :- HaliteType]
  (needs-refinement?-ptn (type-ptn s)))

;;;;

(defn edges-to-adjacent-sets [es keyfn valfn]
  (->
   (zipmap (set (apply concat @*edges)) (repeat #{}))
   (merge (group-by keyfn @*edges))
   (update-keys type-ptn)
   (update-vals #(set (map (comp type-ptn valfn) %)))))

(def *ptn-adjacent-super
  "Map of type patterns to their direct type-pattern super-types"
  (delay (edges-to-adjacent-sets @*edges first second)))

(def *ptn-adjacent-sub
  "Map of type patterns to their direct type-pattern sub-types"
  (delay (edges-to-adjacent-sets @*edges second first)))

(defn distance-walk-depth [adjacents depth init]
  (cons [depth init] (mapcat (partial distance-walk-depth adjacents (inc depth)) (get adjacents init))))

(defn distance-ordered-seq [adjacents init]
  (reverse (distinct (rseq (mapv second (sort-by first (distance-walk-depth adjacents 0 init)))))))

(defn adjacent-map [adjacents]
  (into {} (for [[p pks] adjacents]
             [p (distance-ordered-seq adjacents p)])))

(def *ptn-supertypes
  (delay (adjacent-map @*ptn-adjacent-super)))

(def *ptn-subtypes
  (delay (adjacent-map @*ptn-adjacent-sub)))

(def *ptn-supertypes-set
  (delay (-> (adjacent-map @*ptn-adjacent-super)
             (update-vals set))))

(def *ptn-subtypes-set
  (delay (-> (adjacent-map @*ptn-adjacent-sub)
             (update-vals set))))

(defn genericize-ptn
  "Convert from ptn representing a halite type to a ptn that exists in the graph."
  [p]
  (let [gp (case (:kind p)
             :Instance (assoc p :arg 'KW :r2 'R2)
             (:Set :Vec :Coll) (assoc p :arg 'T)
             p)]
    (assert (get @*ptn-adjacent-super gp) (str "Unknown type pattern:" gp))
    gp))

(s/defn subtype? :- s/Bool
  "True if s is a subtype of t, and false otherwise."
  [s :- HaliteType, t :- HaliteType]
  (or (= s t)
      (let [sp (type-ptn s)
            tp (type-ptn t)
            gsp (genericize-ptn sp)
            gtp (genericize-ptn tp)
            super-ptns (@*ptn-supertypes-set gsp)]
        (if-not (contains? super-ptns gtp)
          false ;; t's graph node does not appear in s's supertypes.
          (if (= :Instance (:kind sp) (:kind tp))
            (instance-subtype? sp tp)
            (if (= 'T (:arg gsp) (:arg gtp))
              ;; If both nodes have type params, those params must be compared
              (subtype? (:arg sp) (:arg tp))
              ;; A lone type param is free, and no params means the node match was sufficient
              true))))))

(s/defn meet :- HaliteType
  "The 'least' supertype of s and t. Formally, return the type m such that all are true:
    (subtype? s m)
    (subtype? t m)
    For all types l, (implies (and (subtype? s l) (subtype? t l)) (subtype? l m))"
  [s :- HaliteType, t :- HaliteType]
  (let [sp (type-ptn s)
        tp (type-ptn t)
        gsp (genericize-ptn sp)
        gtp (genericize-ptn tp)
        s-super-ptns (@*ptn-supertypes-set gsp)
        ;;_ (prn :supers (map ptn-type (@*ptn-supertypes gtp)))
        meet-ptn (if (= :Instance (:kind sp) (:kind tp))
                   (instance-meet sp tp)
                   (some (fn [gtp-super]
                           (when (contains? s-super-ptns gtp-super)
                             ;; gtp-super is a graph node that is the meet node
                             ;;(prn :consider gtp-super)
                             (if (symbol? (:arg gtp-super))
                               ;; If shared node has type param, compare original type params
                               (case (:kind gtp-super)
                                 (:Vec :Set :Coll) (when-let [arg (if (and (:arg sp) (:arg tp))
                                                                    (meet (:arg sp) (:arg tp))
                                                                    (or (:arg sp) (:arg tp)))]
                                                     (assoc gtp-super :arg arg))
                                 (:Instance) (when (apply = (remove nil? [(:arg sp) (:arg tp)]))
                                               (assoc gtp-super
                                                      :arg (some :arg [sp tp])
                                                      :r2 (some :r2 [sp tp]))))
                               ;; No param in meet, return unmodified
                               gtp-super)))
                         (get @*ptn-supertypes gtp)))]
    (ptn-type meet-ptn)))

(s/defn join :- HaliteType
  "The 'greatest' subtype of s and t. Formally, return the type m such that all are true:
    (subtype? m s)
    (subtype? m t)
    For all types l, (implies (and (subtype? l s) (subtype? l t)) (subtype? l m))"
  [s :- HaliteType, t :- HaliteType]
  (let [sp (type-ptn s)
        tp (type-ptn t)
        gsp (genericize-ptn sp)
        gtp (genericize-ptn tp)
        s-sub-ptns (@*ptn-subtypes-set gsp)
        ;;_ (prn :subs (map ptn-type (@*ptn-subtypes gtp)))
        join-ptn (if (= :Instance (:kind sp) (:kind tp))
                   (instance-join sp tp)
                   (some (fn [gtp-sub]
                           (when (contains? s-sub-ptns gtp-sub)
                             ;;(prn :consider gtp-super)
                             (if (symbol? (:arg gtp-sub))
                               ;; If shared node has type param, compare original type params
                               (case (:kind gtp-sub)
                                 (:Vec :Set :Coll) (when-let [arg (if (and (:arg sp) (:arg tp))
                                                                    (join (:arg sp) (:arg tp))
                                                                    (or (:arg sp) (:arg tp)))]
                                                     (assoc gtp-sub :arg arg))
                                 (:Instance) (when (apply = (remove nil? [(:arg sp) (:arg tp)]))
                                               (assoc gtp-sub
                                                      :arg (some :arg [sp tp])
                                                      :r2 (some :r2 [sp tp]))))
                               ;; No param in meet, return unmodified
                               gtp-sub)))
                         (get @*ptn-subtypes gtp)))]
    (ptn-type join-ptn)))

(s/defn elem-type :- (s/maybe HaliteType)
  "Return the type of the element in the collection type given, if it is known.
  Otherwise, or if the given type is not a collection, return nil."
  [t]
  (when (vector? t)
    (let [[x y] t]
      (when (or (= :Set x) (= :Vec x) (= :Coll x))
        y))))

(s/defn types-equivalent? [s :- HaliteType
                           t :- HaliteType]
  (= (dissoc (type-ptn s) :r2)
     (dissoc (type-ptn s) :r2)))

;; helpers for callers to use to avoid making assumptions about how types are represented

(def empty-vector [:Vec :Nothing])

(def empty-set [:Set :Nothing])

(def empty-coll [:Coll :Nothing])

(s/defn concrete-spec-type :- HaliteType
  "Construct a type representing concrete instances of the given spec-id"
  [spec-id :- schema/Keyword]
  [:Instance spec-id])

(s/defn abstract-spec-type :- HaliteType
  "Construct a type representing concrete instances that are to be refined to the given spec-id"
  [spec-id :- schema/Keyword]
  [:Instance :* #{spec-id}])

(s/defn instance-type :- HaliteType
  "Construct a type representing all instances."
  []
  [:Instance :*])

(s/defn no-maybe :- HaliteType
  "If the type represents a 'maybe' type, then remove the maybe."
  [t :- HaliteType]
  (if (strict-maybe-type? t)
    (second t)
    t))

(s/defn maybe-type :- HaliteType
  "Construct a type representing values that are 'maybe' of the given type."
  [t :- HaliteType]
  (if (maybe-type? t)
    t
    [:Maybe t]))

(s/defn vector-type :- HaliteType
  "Construct a type representing vectors of the given type."
  [elem-type :- HaliteType]
  [:Vec elem-type])

(s/defn halite-vector-type? :- s/Bool
  "Return true if this type corresponds to vector values."
  [t :- HaliteType]
  (subtype? t [:Vec :Object]))

(s/defn set-type :- HaliteType
  "Construct a type representing sets of the given type."
  [elem-type :- HaliteType]
  [:Set elem-type])

(s/defn vector-or-set-type :- HaliteType
  "Construct a type representing vectors or sets of the given type. The coll-value is used as an
  example of the type of values to be represented."
  [coll-value
   t :- HaliteType]
  [(cond
     (vector? coll-value) :Vec
     (set? coll-value) :Set)
   t])

(s/defn coll-type :- HaliteType
  "Construct a type representing generic collections of the given type."
  [elem-type :- HaliteType]
  [:Coll elem-type])

(s/defn change-elem-type :- HaliteType
  "Construct a type value that is like coll-type, except it contains new-element-type."
  [coll-type :- HaliteType
   new-elem-type :- HaliteType]
  (ptn-type (assoc (type-ptn coll-type) :arg new-elem-type)))

(s/defn coll-type-string :- String
  "For error messages, produce a string to describe the type of the given coll-type."
  [coll-type :- HaliteType]
  (if (= :Vec (:kind (type-ptn coll-type)))
    "vector"
    "string"))

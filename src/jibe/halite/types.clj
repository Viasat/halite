;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.types
  "Schemata defining the set of halite type terms,
  together with functions that define the subtype
  relation and compute meets and joins."
  (:require [clojure.set :as set]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(def subtypes
  (let [m (fn [x] [:Maybe x])
        T 'T
        KW 'KW
        tv list]
    #{(tv :Nothing #{(tv :Integer :Object :Any)
                     (tv :String :Object)
                     (tv :Boolean :Object)
                     (tv KW :Instance :Object)
                     (tv [:Set T] [:Coll T] :Object)
                     (tv [:Vec T] [:Coll T])})
      (tv :Nothing :Unset #{(tv (m :Integer) :Any)
                            (tv (m :String) :Any)
                            (tv (m :Boolean) :Any)
                            (tv (m KW) (m :Instance) :Any)
                            (tv (m [:Set T]) (m [:Coll T]) :Any)
                            (tv (m [:Vec T]) (m [:Coll T]))})
      (tv :Integer      (m :Integer))
      (tv :String       (m :String))
      (tv :Boolean      (m :Boolean))
      (tv KW (m KW))
      (tv :Instance     (m :Instance))
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
   "rankdir=BT\n"
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
        [kind arg] (cond
                     (vector? t) t
                     (or (= 'KW t) (and (keyword? t) (namespace t))) [:Instance t]
                     :else [t nil])]
    {:maybe? maybe? :kind kind :arg arg}))

(defn ptn-type [{:keys [maybe? kind arg]}]
  (as-> kind t
    (cond
      (= t :Instance) (or arg t)
      arg [t arg]
      :else t)
    (if maybe? [:Maybe t] t)))

#_(defn subtypes-svg []
    (spit "types.dot" (edges-dot @*edges))
    (clojure.java.shell/sh "dot" "-Tpng" "-O" "types.dot"))

(s/defn bare? :- s/Bool
  "true if the symbol or keyword lacks a namespace component, false otherwise"
  [sym-or-kw :- (s/cond-pre s/Keyword s/Symbol)] (nil? (namespace sym-or-kw)))

(def namespaced?
  "true if the symbol or keyword has a namespace component, false otherwise"
  (complement bare?))

(s/defschema BareKeyword (s/constrained s/Keyword bare?))
(s/defschema NamespacedKeyword (s/constrained s/Keyword namespaced?))
(s/defschema BareSymbol (s/constrained s/Symbol bare?))

(s/defn spec-type? :- s/Bool
  "True if t is a namespaced keyword (the type term that represents a spec), false otherwise"
  [t] (and (keyword? t) (namespaced? t)))

(s/defschema TypeAtom
  "Type atoms are always keywords. Namespace-qualified keywords are interpreted as spec ids.
  Unqualified keywords identify built-in scalar types."
  (s/conditional
   spec-type? NamespacedKeyword
   :else (s/enum :Integer :String :Boolean :Object :Nothing :Any :Unset :Instance)))

(declare maybe-type?)

(s/defschema HaliteType
  "A Halite type is either a type atom (keyword), or a 'type constructor' represented
  as a tuple whose first element is the constructor name, and whose remaining elements
  are types.

  Note that the :Set and :Vec constructors do not accept :Maybe types."
  (s/cond-pre
   TypeAtom
   (s/constrained
    [(s/one (s/enum :Set :Vec :Coll :Maybe) "coll-type") (s/one (s/recursive #'HaliteType) "elem-type")]
    (fn [[col-type elem-type]]
      (not (maybe-type? elem-type)))
    :no-nested-maybe)))

(s/defn maybe-type? :- s/Bool
  "True if t is :Unset or [:Maybe T], false otherwise."
  [t :- HaliteType]
  (or (= :Unset t)
      (and (vector? t) (= :Maybe (first t)))))

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

(defn genericize-ptn [p]
  (let [gp (update p :arg #(when %
                             (if (= :Instance (:kind p))
                               'KW
                               'T)))]
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
          (if (every? symbol? [(:arg gtp) (:arg gsp)])
            ;; If both nodes have type params, those params must be compared
            (do
              (assert (= (:arg gsp) (:arg gtp))
                      (str "Type graph musn't mix meaning of T in same subtype relation: "
                           (pr-str sp tp)))
              (case (:kind sp)
                (:Vec :Set :Coll) (subtype? (:arg sp) (:arg tp))
                (:Instance) (= (:arg sp) (:arg tp))))
            ;; A lone type param is free, and no params means the node match was sufficient
            true)))))

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
        meet-ptn (some (fn [gtp-super]
                         (when (contains? s-super-ptns gtp-super)
                           ;;(prn :consider gtp-super)
                           (if (symbol? (:arg gtp-super))
                             ;; If shared node has type param, compare original type params
                             (case (:kind gtp-super)
                               (:Vec :Set :Coll) (when-let [arg (if (and (:arg sp) (:arg tp))
                                                                  (meet (:arg sp) (:arg tp))
                                                                  (or (:arg sp) (:arg tp)))]
                                                   (assoc gtp-super :arg arg))
                               (:Instance) (when (apply = (remove nil? [(:arg sp) (:arg tp)]))
                                             (assoc gtp-super :arg (some :arg [sp tp]))))
                             ;; No param in meet, return unmodified
                             gtp-super)))
                       (get @*ptn-supertypes gtp))]
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
        join-ptn (some (fn [gtp-sub]
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
                                             (assoc gtp-sub :arg (some :arg [sp tp]))))
                             ;; No param in meet, return unmodified
                             gtp-sub)))
                       (get @*ptn-subtypes gtp))]
    (ptn-type join-ptn)))

(s/defn elem-type :- (s/maybe HaliteType)
  "Return the type of the element in the collection type given, if it is known.
  Otherwise, or if the given type is not a collection, return nil."
  [t]
  (when (vector? t)
    (let [[x y] t]
      (when (or (= :Set x) (= :Vec x) (= :Coll x))
        y))))

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
    (= 'KW (:arg t)) (:r2 t)
    :else (conj (or (:r2 t) #{}) (:arg t))))

(defn- remove-redundant-r2 [t]
  (if (contains? (or (:r2 t) #{}) (:arg t))
    (no-nil (assoc t :r2 (no-empty (disj (:r2 t) (:arg t)))))
    t))

(defn instance-subtype? [s t]
  (and (set/subset? (all-r2 t) (all-r2 s))
       (or (= (:arg s) (:arg t))
           (= 'KW (:arg t)))
       (or (= (:maybe? s) (:maybe? t))
           (:maybe? t))))

(defn instance-meet [s t]
  (remove-redundant-r2
   (no-nil {:maybe? (or (:maybe? s) (:maybe? t))
            :kind :Instance
            :arg (cond
                   (= (:arg s) (:arg t)) (:arg s)
                   :else 'KW)
            :r2 (no-empty (cond
                            (and (not= 'KW (:arg s))
                                 (not= 'KW (:arg t)))
                            #{}

                            (or (set/subset? (all-r2 t) (all-r2 s))
                                (set/subset? (all-r2 s) (all-r2 t)))
                            (set/intersection (all-r2 s) (all-r2 t))

                            :else #{}))})))

(defn instance-join [s t]
  (remove-redundant-r2 (cond
                         (or (= (:arg s) (:arg t))
                             (= 'KW (:arg s))
                             (= 'KW (:arg t)))
                         (no-nil {:maybe? (and (:maybe? s) (:maybe? t))
                                  :kind :Instance
                                  :arg (cond
                                         (= (:arg s) (:arg t)) (:arg s)
                                         (= 'KW (:arg s)) (:arg t)
                                         (= 'KW (:arg t)) (:arg s))
                                  :r2 (no-empty (set/union (all-r2 s) (all-r2 t)))})

                         :else
                         (if (and (:maybe? s) (:maybe? t))
                           :Unset
                           :Nothing))))

(defn instance-type [s]
  (when (= 1 (count (all-r2 s)))
    (if (= 'KW (:arg s))
      (first (:r2 s))
      (:arg s))))

(defn instance-needs-refinement? [s]
  (= 'KW (:arg s)))

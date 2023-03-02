;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.analysis
  (:require [clojure.set :as set]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.lib.graph :as graph]
            [com.viasat.halite.types :as types]
            [schema.core :as s])
  (:import [clojure.lang ExceptionInfo]
           [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(envs/init)

(defn- third [s]
  (second (rest s)))

(defn- remove-nil-vals [m]
  (select-keys m (keep (fn [[k v]] (when (some? v) k)) m)))

(defn- no-empty
  "Convert empty collection to nil"
  [coll]
  (when (seq coll)
    coll))

(def NumericValue (s/conditional
                   base/integer-or-long? Number
                   base/fixed-decimal? FixedDecimal))

(def MinRange {:min NumericValue
               :min-inclusive Boolean
               (s/optional-key :optional) (s/eq true)})

(def MaxRange {:max NumericValue
               :max-inclusive Boolean
               (s/optional-key :optional)  (s/eq true)})

(def MinMaxRange {:min NumericValue
                  :min-inclusive Boolean
                  :max NumericValue
                  :max-inclusive Boolean
                  (s/optional-key :optional)  (s/eq true)})

(def Range (s/conditional
            #(and (:min %) (:max %)) MinMaxRange
            #(:min %) MinRange
            #(:max %) MaxRange))

(def RangeConstraint {:ranges #{Range}})

(def EnumConstraint {:enum #{s/Any}
                     (s/optional-key :optional) (s/eq true)})

(def Natural (s/constrained Number #(and (integer? %)
                                         (>= % 0))))

(declare TopLevelFieldConstraint)

(def CollectionConstraint {(s/optional-key :coll-size) Natural
                           (s/optional-key :coll-elements) (s/recursive #'TopLevelFieldConstraint)
                           (s/optional-key :enum) #{s/Any}})

(def TopLevelFieldConstraint (s/conditional
                              :coll-size CollectionConstraint
                              :coll-elements CollectionConstraint
                              :enum EnumConstraint
                              :ranges RangeConstraint
                              #(= :none %) (s/eq :none)
                              #(= :some %) (s/eq :some)))

(declare replace-free-vars)

(defn- as-optional-var [expr]
  (when (and (seq? expr)
             (= 3 (count expr))
             (= 'when-value (first expr))
             (= (second expr) (third expr))
             (symbol? (second expr)))
    (second expr)))

(defn- replace-seq-free-vars [var-map context expr]
  (cond
    (= 'let (first expr))
    (let [[_ bindings body] expr
          [context bindings'] (reduce
                               (fn [[context bindings'] [sym e]]
                                 [(conj context sym) (conj bindings' sym (replace-free-vars var-map context e))])
                               [context []]
                               (partition 2 bindings))]
      (list 'let bindings'
            (replace-free-vars var-map context body)))

    (#{'every? 'any?} (first expr))
    (let [[_ [sym coll] body] expr]
      (list (first expr) [sym (replace-free-vars var-map context coll)]
            (replace-free-vars var-map (conj context sym) body)))

    (= 'if-value (first expr))
    (let [[_ s then else] expr
          replacement-expr (var-map s)
          optional-var (as-optional-var replacement-expr)]

      (cond
        optional-var
        (let [new-var-map (assoc var-map s optional-var)]
          (list 'if-value optional-var
                (replace-free-vars new-var-map context then)
                (replace-free-vars new-var-map context else)))

        (seq? replacement-expr)
        (let [new-var-map (assoc var-map s s)]
          (list 'if-value-let [s replacement-expr]
                (replace-free-vars new-var-map context then)
                (replace-free-vars new-var-map context else)))

        :default
        (replace-free-vars var-map context then)))

    (= 'when-value (first expr))
    (let [[_ s then] expr
          replacement-expr (var-map s)
          optional-var (as-optional-var replacement-expr)]

      (cond
        optional-var
        (let [new-var-map (assoc var-map s optional-var)]
          (list 'when-value optional-var
                (replace-free-vars new-var-map context then)))

        (seq? replacement-expr)
        (let [new-var-map (assoc var-map s s)]
          (list 'when-value-let [s replacement-expr]
                (replace-free-vars new-var-map context then)))

        :default
        (replace-free-vars var-map context then)))

    :default
    (apply list (first expr) (->> expr
                                  rest
                                  (map (partial replace-free-vars var-map context))))))

(defn- replace-collection-free-vars [var-map context expr]
  (into (empty expr)
        (->> expr
             (map (partial replace-free-vars var-map context)))))

(defn replace-free-vars
  "Recursively replace free-vars according to var-map"
  ([var-map expr]
   (replace-free-vars var-map #{} expr))
  ([var-map context expr]
   (cond
     (boolean? expr) expr
     (base/integer-or-long? expr) expr
     (base/fixed-decimal? expr) expr
     (string? expr) expr
     (symbol? expr) (if (context expr)
                      expr
                      (var-map expr expr))
     (keyword? expr) expr
     (map? expr) (update-vals expr
                              (partial replace-free-vars var-map context))
     (seq? expr) (replace-seq-free-vars var-map context expr)
     (set? expr) (replace-collection-free-vars var-map context expr)
     (vector? expr) (replace-collection-free-vars var-map context expr)
     :default (throw (ex-info "unexpected expr to replace-free-vars" {:expr expr})))))

;;

(declare gather-free-vars)

(defn- gather-seq-free-vars [context expr]
  (cond
    (= 'let (first expr))
    (let [[_ bindings body] expr
          [context free-vars] (reduce
                               (fn [[context free-vars] [sym e]]
                                 [(conj context sym) (into free-vars (gather-free-vars context e))])
                               [context #{}]
                               (partition 2 bindings))]
      (into free-vars (gather-free-vars context body)))

    (#{'every? 'any?} (first expr))
    (let [[_ [sym coll] body] expr]
      (into (gather-free-vars context coll)
            (gather-free-vars (conj context sym) body)))

    :default
    (->> expr
         rest
         (map (partial gather-free-vars context))
         (reduce into #{}))))

(defn- gather-collection-free-vars [context expr]
  (->> expr
       (map (partial gather-free-vars context))
       (reduce into #{})))

(defn gather-free-vars
  "Recursively find the set of symbols in the expr which refer to the surrounding context."
  ([expr]
   (gather-free-vars #{} expr))
  ([context expr]
   (cond
     (boolean? expr) #{}
     (base/integer-or-long? expr) #{}
     (base/fixed-decimal? expr) #{}
     (string? expr) #{}
     (symbol? expr) (if (or (context expr)
                            (= \$ (first (name expr))) ;; halite reserved symbols start with \$
                            )
                      #{}
                      #{expr})
     (keyword? expr) #{}
     (map? expr) (->> (vals expr)
                      (map (partial gather-free-vars context))
                      (reduce into #{}))
     (seq? expr) (gather-seq-free-vars context expr)
     (set? expr) (gather-collection-free-vars context expr)
     (vector? expr) (gather-collection-free-vars context expr)
     :default (throw (ex-info "unexpected expr to gather-free-vars" {:expr expr})))))

;;

(declare gather-referenced-spec-ids)

(defn- gather-seq-referenced-spec-ids [expr]
  (cond
    (= 'let (first expr))
    (let [[_ bindings body] expr
          spec-ids (reduce
                    (fn [spec-ids [sym e]]
                      (into spec-ids (gather-referenced-spec-ids e)))
                    #{}
                    (partition 2 bindings))]
      (into spec-ids (gather-referenced-spec-ids body)))

    (#{'every? 'any?} (first expr))
    (let [[_ [sym coll] body] expr]
      (into (gather-referenced-spec-ids coll)
            (gather-referenced-spec-ids body)))

    :default
    (->> expr
         rest
         (map gather-referenced-spec-ids)
         (reduce into #{}))))

(defn- gather-collection-referenced-spec-ids [expr]
  (->> expr
       (map gather-referenced-spec-ids)
       (reduce into #{})))

(defn gather-referenced-spec-ids
  "Recursively find the set of spec-ids referenced by the expr."
  [expr]
  (cond
    (boolean? expr) #{}
    (base/integer-or-long? expr) #{}
    (base/fixed-decimal? expr) #{}
    (string? expr) #{}
    (symbol? expr) #{}
    (keyword? expr) (if (types/namespaced-keyword? expr)
                      #{expr}
                      #{})
    (map? expr) (->> (vals expr)
                     (map gather-referenced-spec-ids)
                     (reduce into #{}))
    (seq? expr) (gather-seq-referenced-spec-ids expr)
    (set? expr) (gather-collection-referenced-spec-ids expr)
    (vector? expr) (gather-collection-referenced-spec-ids expr)
    :default (throw (ex-info "unexpected expr to gather-referenced-spec-ids" {:expr expr}))))

;;;;

(defn- simple-value-or-symbol? [expr]
  (or
   (boolean? expr)
   (base/integer-or-long? expr)
   (base/fixed-decimal? expr)
   (string? expr)
   (symbol? expr)
   (map? expr)
   (set? expr)
   (vector? expr)))

(defn- condense-boolean-logic [expr]
  (cond
    (and (seq? expr)
         (= 'and (first expr))) (let [args (->> expr
                                                rest
                                                (map condense-boolean-logic)
                                                (remove #(= % true))
                                                (mapcat #(if (and (seq? %)
                                                                  (= 'and (first %)))
                                                           (rest %)
                                                           [%])))]
                                  (condp = (count args)
                                    0 true
                                    1 (first args)
                                    (cons 'and args)))
    (and (seq? expr)
         (= 'or (first expr))) (let [args (->> expr
                                               rest
                                               (map condense-boolean-logic)
                                               (remove #(= % false)))]
                                 (if (some #(= % true) args)
                                   true
                                   (condp = (count args)
                                     0 false
                                     1 (first args)
                                     (cons 'or args))))
    :default expr))

(defn sort-tlfc [expr]
  (cond
    (and (seq? expr)
         (= 'and (first expr))) (-> (group-by gather-free-vars (rest expr))
                                    (update-keys first)
                                    (update-vals #(condense-boolean-logic (cons 'and %))))
    (= true expr) nil

    (= 1 (count (gather-free-vars expr))) {(first (gather-free-vars expr))
                                           expr}

    :default (throw (ex-info "unexpected expression" {:expr expr}))))

(defn- tlfc-data-or [xs0]
  (let [no-value-predicate (fn [x]
                             (= x :none))
        optional? (some no-value-predicate xs0)
        xs (remove no-value-predicate xs0)
        result
        (if (some #(not (nil? (:enum %))) xs)
          (if (every? #(not (nil? (:enum %))) xs)
            (reduce (fn [a b]
                      (remove-nil-vals {:enum (set/union (:enum a) (:enum b))
                                        :optional (when (and (or (:optional a)
                                                                 (= :unknown a))
                                                             (:optional b))
                                                    true)}))
                    {:enum #{}
                     :optional :unknown} xs)
            nil)
          {:ranges (set (mapcat
                         #(or (:ranges %)
                              [%])
                         xs))})]
    (if optional?
      (assoc result :optional true)
      result)))

(defn- combine-mins [a b]
  (cond
    (base/h< (:min a) (:min b)) (merge a b)
    (base/h> (:min a) (:min b)) (merge b a)
    (= (:min a) (:min b)) (assoc (merge a b)
                                 :min-inclusive (and (:min-inclusive a)
                                                     (:min-inclusive b)))))

(defn- combine-maxs [a b]
  (cond
    (base/h< (:max a) (:max b)) (merge b a)
    (base/h> (:max a) (:max b)) (merge a b)
    (= (:max a) (:max b)) (assoc (merge a b)
                                 :max-inclusive (and (:max-inclusive a)
                                                     (:max-inclusive b)))))

(defn- get-min [a]
  (or (:min a)
      Long/MIN_VALUE))

(defn- get-max [a]
  (or (:max a)
      Long/MAX_VALUE))

(defn- combine-mins-or [a b]
  (cond
    (base/h< (get-min a) (get-min b)) (merge b a)
    (base/h> (get-min a) (get-min b)) (merge a b)
    (= (get-min a) (get-min b)) (assoc (merge a b)
                                       :min-inclusive (or (:min-inclusive a)
                                                          (:min-inclusive b)))))

(defn- combine-maxs-or [a b]
  (cond
    (base/h< (get-max a) (get-max b)) (merge a b)
    (base/h> (get-max a) (get-max b)) (merge b a)
    (= (get-max a) (get-max b)) (assoc (merge a b)
                                       :max-inclusive (or (:max-inclusive a)
                                                          (:max-inclusive b)))))

(defn- tlfc-data-and-enum [xs]
  (let [some? (some #(= :some %) xs)
        result (reduce (fn [a b]
                         (if (or (= :none a)
                                 (= :none b))
                           :none
                           (let [combined {:enum (cond
                                                   (and (:enum a) (:enum b)) (set/intersection (:enum a) (:enum b))
                                                   (:enum a) (:enum a)
                                                   (:enum b) (:enum b))}]
                             (remove-nil-vals (assoc combined
                                                     :optional (when (and (not some?)
                                                                          (or (and (:enum a) (:optional a) (= nil (:enum b)))
                                                                              (and (:enum b) (:optional b) (= nil (:enum a)))
                                                                              (and (:enum a) (:enum b) (:optional a) (:optional b))))
                                                                 true))))))
                       {}
                       xs)]
    (if (= :none result)
      result
      (-> result
          remove-nil-vals
          no-empty))))

(defn- reduce-ranges [xs]
  (reduce (fn [a b]
            (let [result (cond
                           (or (= :none b)
                               (= :none a)) :none

                           (= :some a) b

                           (= :some b) a

                           (and (:min a) (:min b)
                                (:max a) (:max b))
                           (merge (select-keys (combine-mins a b) [:min :min-inclusive :optional])
                                  (select-keys (combine-maxs a b) [:max :max-inclusive :optional]))

                           (and (:min a) (:min b))
                           (combine-mins a b)

                           (and (:max a) (:max b))
                           (combine-maxs a b)

                           :default
                           (merge a b))]
              (if (and (map? result)
                       (not (and (or (:optional a)
                                     (= :unknown (:optional a))
                                     (and (seq? (:ranges a))
                                          (every? :optional (:ranges a))))
                                 (or (:optional b)
                                     (and (seq? (:ranges a))
                                          (every? :optional (:ranges b)))))))
                (dissoc result :optional)
                result)))
          {:optional :unknown}
          (remove nil? xs)))

(defn- tlfc-data-and-ranges [xs]
  (let [result (if-let [r (some #(:ranges %) xs)]
                 (let [reduced-ranges0 (reduce-ranges xs)]
                   (if (= :none reduced-ranges0)
                     :none
                     {:ranges (let [reduced-range (no-empty (select-keys reduced-ranges0 [:min :min-inclusive :max :max-inclusive :optional]))]
                                (->> (let [range-sets (->> (filter :ranges xs)
                                                           (map :ranges)
                                                           (sort-by count))]
                                       (reduce (fn [r1 r2]
                                                 (let [reduced-r1 (reduce-ranges r1)]
                                                   (map #(reduce-ranges [% reduced-r1]) r2)))
                                               (first range-sets)
                                               (rest range-sets)))
                                     (map #(reduce-ranges [% reduced-range]))
                                     set))}))
                 (if (some #(and (map? %)
                                 (or (:coll-size %)
                                     (:coll-elements %))) xs)
                   (when (every? #(and (map? %)
                                       (or (:coll-size %)
                                           (:coll-elements %))) xs)
                     (reduce (partial merge-with merge) {} xs))
                   (let [reduced-ranges (reduce-ranges xs)]
                     (if (= :none reduced-ranges)
                       :none
                       {:ranges #{(dissoc reduced-ranges :enum)}}))))
        some? (some #(= :some %) xs)]
    (if some?
      (update-in result [:ranges]
                 (fn [ranges]
                   (->> ranges
                        (map #(dissoc % :optional)))))
      result)))

(defn- range-to-predicate [r]
  (let [{:keys [max max-inclusive min min-inclusive]} r]
    (fn [x]
      (and (cond
             (and max max-inclusive) (base/h<= x max)
             max (base/h< x max)
             :default true)
           (cond
             (and min min-inclusive) (base/h>= x min)
             min (base/h> x min)
             :default true)))))

(defn- apply-ranges-to-enum
  [enum ranges]
  (set (filter #(some (fn [p]
                        (p %))
                      (map range-to-predicate ranges)) enum)))

(defn- tlfc-data-and [xs]
  (let [aggregate-enum (tlfc-data-and-enum xs)
        aggregate-ranges (tlfc-data-and-ranges xs)]
    (if (= :none aggregate-enum)
      :none
      (if (and aggregate-enum aggregate-ranges)
        (remove-nil-vals {:enum (apply-ranges-to-enum (:enum aggregate-enum) (:ranges aggregate-ranges))
                          :optional (when (= true (:optional aggregate-enum)) true)})
        (or aggregate-enum aggregate-ranges)))))

(defn- tlfc-data* [optional-vars expr]
  (cond
    (and (seq? expr)
         (= 'if-value (first expr))) (tlfc-data* (conj optional-vars (second expr))
                                                 (third expr))

    (and (seq? expr)
         (= '= (first expr))) (if (some (fn [x]
                                          (and (seq? x)
                                               (= 2 (count x))
                                               (= 'count (first x))
                                               (symbol? (second x)))) (rest expr))
                                (cond
                                  (and (seq? (second expr))
                                       (integer? (third expr))) {:coll-size (third expr)}
                                  (and (seq? (third expr))
                                       (integer? (second expr))) {:coll-size (second expr)}
                                  :default true)
                                (if (some #(= '$no-value %) (rest expr))
                                  :none
                                  (remove-nil-vals {:enum (->> (rest expr)
                                                               (remove symbol?)
                                                               set)
                                                    :optional (when-let [v (first (filter symbol? (rest expr)))]
                                                                (when (optional-vars v)
                                                                  true))})))

    (and (seq? expr)
         (= 'not= (first expr))) (if (some #(= '$no-value %) (rest expr))
                                   :some
                                   true)

    (and (seq? expr)
         (#{'< '<= '> '>=} (first expr))) (let [range (if (symbol? (second expr))
                                                        (condp = (first expr)
                                                          '< {:max (third expr)
                                                              :max-inclusive false}
                                                          '<= {:max (third expr)
                                                               :max-inclusive true}
                                                          '> {:min (third expr)
                                                              :min-inclusive false}
                                                          '>= {:min (third expr)
                                                               :min-inclusive true})
                                                        (condp = (first expr)
                                                          '< {:min (second expr)
                                                              :min-inclusive true}

                                                          '<= {:min (second expr)
                                                               :min-inclusive false}
                                                          '> {:max (second expr)
                                                              :max-inclusive true}
                                                          '>= {:max (second expr)
                                                               :max-inclusive false}))
                                                s (if (symbol? (second expr))
                                                    (second expr)
                                                    (third expr))]
                                            (remove-nil-vals (assoc range :optional (when (optional-vars s)
                                                                                      true))))

    (and (seq? expr)
         (= 'and (first expr))) (->> (rest expr)
                                     (map (partial tlfc-data* optional-vars))
                                     tlfc-data-and)

    (and (seq? expr)
         (= 'or (first expr))) (->> (rest expr)
                                    (map (partial tlfc-data* optional-vars))
                                    tlfc-data-or)

    (and (seq? expr)
         (= 'contains? (first expr))) (let [core-result {:enum (second expr)}]
                                        (if (optional-vars (third expr))
                                          (assoc core-result :optional true)
                                          core-result))

    (and (vector? expr)) {:coll-elements (let [result (tlfc-data* optional-vars (first expr))]
                                           (if (and (map? result)
                                                    (or (contains? result :min)
                                                        (contains? result :max)))
                                             {:ranges #{result}}
                                             result))}

    :default expr))

(defn- combine-max-only-ranges [ranges]
  (let [max-only (filter #(nil? (:min %)) ranges)
        others (remove #(nil? (:min %)) ranges)]
    (if (> (count max-only) 1)
      (conj others (reduce combine-maxs-or (first max-only) (rest max-only)))
      ranges)))

(defn- combine-min-only-ranges [ranges]
  (let [min-only (filter #(nil? (:max %)) ranges)
        others (remove #(nil? (:max %)) ranges)]
    (if (> (count min-only) 1)
      (conj others (reduce combine-mins-or (first min-only) (rest min-only)))
      ranges)))

(defn- combine-single-leg-ranges [x]
  (if (:ranges x)
    (assoc x :ranges (->> (:ranges x)
                          combine-max-only-ranges
                          combine-min-only-ranges
                          set))
    x))

(defn- ranges-overlap? [a b]
  (let [min-a (or (:min a)
                  Long/MIN_VALUE)
        min-b (or (:min b)
                  Long/MIN_VALUE)
        max-a (or (:max a)
                  Long/MAX_VALUE)
        max-b (or (:max b)
                  Long/MAX_VALUE)]
    (or (base/h< min-a min-b max-a)
        (base/h< min-a max-b max-a)
        (base/h< min-b min-a max-b)
        (base/h< min-b max-a max-b))))

(defn- touch-min [a b]
  (= (:min a) (:min b)))

(defn- touch-max [a b]
  (= (:max a) (:max b)))

(defn- touch-min-max [a b]
  (let [min-a (or (:min a)
                  Long/MIN_VALUE)
        min-b (or (:min b)
                  Long/MIN_VALUE)
        max-a (or (:max a)
                  Long/MAX_VALUE)
        max-b (or (:max b)
                  Long/MAX_VALUE)]
    (or (= min-a max-b)
        (= min-b max-a))))

(defn- combine-ranges [a b]
  (merge (select-keys (combine-mins-or a b) [:min :min-inclusive])
         (select-keys (combine-maxs-or a b) [:max :max-inclusive])))

(defn- remove-overlapping [x]
  (if-let [ranges (:ranges x)]
    (loop [[r1 & remaining] (set ranges)
           output []]
      (if r1
        (let [[combined-r1 other-1] (loop [result r1
                                           [r2 & remaining2] remaining
                                           todo []]
                                      (if r2
                                        (if (or (ranges-overlap? r1 r2)
                                                (touch-min-max r1 r2)
                                                (touch-min r1 r2)
                                                (touch-max r1 r2))
                                          (recur (combine-ranges r1 r2)
                                                 remaining2
                                                 todo)
                                          (recur result
                                                 remaining2
                                                 (conj todo r2)))
                                        [result todo]))]
          (recur other-1 (conj output combined-r1)))
        (assoc x :ranges (set output))))
    x))

(defn- tlfc-data [expr]
  (->> (let [result (tlfc-data* #{} expr)]
         (if (and (map? result)
                  (or (contains? result :min)
                      (contains? result :max)))
           {:ranges #{result}}
           result))
       combine-single-leg-ranges
       remove-overlapping))

(s/defn tlfc-data-map :- {s/Symbol TopLevelFieldConstraint}
  [m]
  (->> (update-vals m tlfc-data)
       remove-nil-vals
       ;; (remove #(= true (second %)))
       (into {})))

(defn- gather-tlfc-single*
  [expr]
  (cond
    (and (seq? expr)
         (= 'and (first expr))) (->> expr
                                     rest
                                     (map gather-tlfc-single*)
                                     (cons 'and))

    (and (seq? expr)
         (#{'= '< '<= '> '>=} (first expr))) (if (and (= 1 (count (gather-free-vars expr)))
                                                      (->> (rest expr)
                                                           (every? simple-value-or-symbol?))
                                                      (first (filter symbol? (rest expr))))
                                               expr
                                               true)

    (and (seq? expr)
         (= 'contains? (first expr))) (let [[_ coll e] expr]
                                        (if (and (= 1 (count (gather-free-vars expr)))
                                                 (->> (rest expr)
                                                      (every? simple-value-or-symbol?))
                                                 (symbol? e))
                                          expr
                                          true))
    :default true))

(defn- gather-tlfc*
  [expr]
  (cond
    (and (seq? expr)
         (= 'if-value (first expr))
         (= 1 (count (gather-free-vars expr)))) (list 'if-value
                                                      (second expr)
                                                      (->> expr
                                                           third
                                                           (gather-tlfc*)))

    (and (seq? expr)
         (= 'and (first expr))) (->> expr
                                     rest
                                     (map gather-tlfc*)
                                     (cons 'and))
    (and (seq? expr)
         (= 'or (first expr))
         (= 1 (count (gather-free-vars expr)))) (->> expr
                                                     rest
                                                     (map gather-tlfc-single*)
                                                     (cons 'or))

    (and (seq? expr)
         (#{'= '< '<= '> '>=} (first expr))) (if (and (= 1 (count (gather-free-vars expr)))
                                                      (or (and (->> (rest expr)
                                                                    (every? simple-value-or-symbol?))
                                                               (first (filter symbol? (rest expr))))
                                                          (and (= '= (first expr))
                                                               (some (fn [x]
                                                                       (and (seq? x)
                                                                            (= 2 (count x))
                                                                            (= 'count (first x))
                                                                            (symbol? (second x)))) (rest expr)))))
                                               expr
                                               true)

    (and (seq? expr)
         (= 'not= (first expr))) (if (and (= 3 (count expr))
                                          (= 1 (count (gather-free-vars expr)))
                                          (some #(= '$no-value %) (rest expr)))
                                   expr
                                   true)

    (and (seq? expr)
         (= 'contains? (first expr))) (let [[_ coll e] expr]
                                        (if (and (= 1 (count (gather-free-vars expr)))
                                                 (->> (rest expr)
                                                      (every? simple-value-or-symbol?))
                                                 (symbol? e))
                                          expr
                                          true))

    (and (seq? expr)
         (= 'every? (first expr))) (let [[_ [sym v] e] expr]
                                     (if (and (= 1 (count (gather-free-vars expr)))
                                              (= 1 (count (gather-free-vars v)))
                                              (symbol? v)
                                              (= 1 (count (gather-free-vars e)))
                                              (= #{sym} (gather-free-vars e)))
                                       [(replace-free-vars {sym v}
                                                           (gather-tlfc* e))]
                                       true))

    :default true))

(s/defn gather-tlfc
  "Produce a map from symbols to sets of all of the 'top-level-field-constraints' contained in the
  boolean expr. These are expressions that are clauses of the expression which only refer to a
  single variable, which are of a form that can be understood simply. These are necessary, but not
  sufficient constraints for the entire expression to evaluate to 'true'."
  [expr]
  (->> expr gather-tlfc* condense-boolean-logic))

;;;;

(s/defn ^:private fixed-decimal-to-long [f]
  (let [scale (fixed-decimal/get-scale f)]
    (fixed-decimal/shift-scale f scale)))

(s/defn ^:private range-size* [min max]
  (if (base/integer-or-long? min)
    (- max min)
    (fixed-decimal-to-long (fixed-decimal/f- max min))))

(s/defn ^:private range-size [r :- Range]
  (let [{:keys [min max min-inclusive max-inclusive]} r]
    (when (or (and (base/integer-or-long? min)
                   (base/integer-or-long? max))
              (and (base/fixed-decimal? min)
                   (base/fixed-decimal? max)
                   (= (fixed-decimal/get-scale min)
                      (fixed-decimal/get-scale max))))
      (clojure.core/max (let [{:keys [min max min-inclusive max-inclusive]} r]
                          (- (+ (range-size* min max) (if max-inclusive 1 0))
                             (if (not min-inclusive)
                               1
                               0)))
                        0))))

(s/defn ^:private range-set-size [rs :- #{Range}]
  (let [sizes (map range-size rs)]
    (when (every? identity sizes)
      (apply + sizes))))

(s/defn make-fixed-decimal-string [n scale]
  (str (BigDecimal. (BigInteger. (str n))
                    ^int scale)))

(s/defn ^:private enumerate-range [r :- Range]
  (let [{:keys [min max min-inclusive max-inclusive]} r]
    (if (base/integer-or-long? min)
      (range (if min-inclusive
               min
               (inc min))
             (if max-inclusive
               (inc max)
               max))
      (let [scale (fixed-decimal/get-scale min)]
        (->> (range (if min-inclusive
                      (fixed-decimal-to-long min)
                      (inc (fixed-decimal-to-long min)))
                    (if max-inclusive
                      (inc (fixed-decimal-to-long max))
                      (fixed-decimal-to-long max)))
             (map #(make-fixed-decimal-string % scale))
             (map fixed-decimal/fixed-decimal-reader))))))

(s/defn ^:private enumerate-range-set [rs :- #{Range}]
  (->> rs
       (mapcat enumerate-range)
       set))

(def ^:dynamic *max-enum-size* 1000)

(s/defn ^:private small-ranges-to-enums [tlfc-map :- {s/Symbol TopLevelFieldConstraint}]
  (-> tlfc-map
      (update-vals (fn [tlfc]
                     (if (and (:ranges tlfc)
                              (range-set-size (:ranges tlfc))
                              (< (range-set-size (:ranges tlfc)) *max-enum-size*))
                       (-> tlfc
                           (assoc :enum (enumerate-range-set (:ranges tlfc))
                                  :optional (when (some :optional (:ranges tlfc))
                                              true))
                           remove-nil-vals
                           (dissoc :ranges))
                       tlfc)))))

;;;;

(s/defn compute-tlfc-map [tree]
  (-> tree
      gather-tlfc
      sort-tlfc
      tlfc-data-map
      small-ranges-to-enums))

;;;;

(s/defschema TailSpecRef {:tail types/NamespacedKeyword
                          (s/optional-key :sym) [s/Symbol]})

(s/defschema SpecRef (s/conditional
                      map? TailSpecRef
                      :else types/NamespacedKeyword))

(s/defn ^:private tail-ref? :- Boolean
  [expr :- SpecRef]
  (boolean (and (map? expr)
                (or (= 1 (count expr))
                    (= 2 (count expr)))
                (contains? expr :tail))))

(declare find-spec-refs)

(s/defn ^:private wrap-tail :- TailSpecRef
  [expr :- types/NamespacedKeyword]
  {:tail expr})

(s/defn ^:private unwrap-tail :- types/NamespacedKeyword
  [expr :- SpecRef]
  (if (tail-ref? expr)
    (:tail expr)
    expr))

(s/defn ^:private unwrap-tail-set :- #{types/NamespacedKeyword}
  [s :- (s/maybe #{SpecRef})]
  (->> s
       (map unwrap-tail)
       set))

(s/defn ^:private wrap-sym :- SpecRef
  [s :- s/Symbol
   expr :- SpecRef]
  (if (tail-ref? expr)
    (update expr :sym conj s)
    expr))

(s/defn ^:private find-collection-spec-refs :- #{SpecRef}
  [context expr]
  (->> expr
       (map (partial find-spec-refs context))
       (reduce into #{})))

(s/defn ^:private unwrap-tail-syms
  [binding-pairs sub-results-map ignore-sym-set inner-result]
  (let [{inner-tail-results true inner-other-results false} (group-by tail-ref? inner-result)
        {:keys [inner-tail-results
                post-sub-results]} (->> binding-pairs
                                        reverse
                                        (reduce (fn [{:keys [inner-tail-results post-sub-results] :as p} [s _]]
                                                  (let [match? (some #(= (first (:sym %)) s) inner-tail-results)]
                                                    (if match?
                                                      {:inner-tail-results (->> inner-tail-results
                                                                                (map #(if (= (first (:sym %)) s)
                                                                                        (remove-nil-vals (update % :sym (comp no-empty rest)))
                                                                                        %)))
                                                       ;; skip the items that are already reflected in tail result
                                                       :post-sub-results post-sub-results}
                                                      {:inner-tail-results (->> inner-tail-results
                                                                                (map
                                                                                 #(if (ignore-sym-set (first (:sym %)))
                                                                                    (remove-nil-vals (update % :sym (comp no-empty rest)))
                                                                                    %)))
                                                       :post-sub-results (into post-sub-results
                                                                               (get sub-results-map s))})))
                                                {:inner-tail-results inner-tail-results
                                                 :post-sub-results #{}}))]
    (into (into post-sub-results
                inner-other-results)
          inner-tail-results)))

(s/defn ^:private find-seq-spec-refs :- #{SpecRef}
  [context expr]
  (cond
    (= 'let (first expr))
    (let [[_ bindings body] expr
          binding-pairs (->> bindings
                             (partition 2))
          {context' :context
           :keys [sub-results-map]} (->> binding-pairs
                                         (reduce (fn [{:keys [context sub-results-map]} [s e]]
                                                   (let [sub-result (find-spec-refs context e)
                                                         unwrapped-sub-result (unwrap-tail-set sub-result)]
                                                     {:context (assoc context s sub-result)
                                                      :sub-results-map (assoc sub-results-map
                                                                              s unwrapped-sub-result)}))
                                                 {:context context
                                                  :sub-results-map {}}))]
      (unwrap-tail-syms binding-pairs sub-results-map #{} (find-spec-refs context' body)))

    (#{'if 'if-value} (first expr))
    (let [[_ check then else] expr]
      (into (into (unwrap-tail-set (find-spec-refs context check))
                  (find-spec-refs context then))
            (find-spec-refs context else)))

    (= 'cond (first expr))
    ;; turn 'cond into nested 'ifs for analysis purposes
    (find-seq-spec-refs context (reduce (fn [if-expr [pred then]]
                                          (list 'if pred then if-expr))
                                        (last expr)
                                        (reverse (partition 2 (rest expr)))))

    (#{'when 'when-value} (first expr))
    (let [[_ check then] expr]
      (into (unwrap-tail-set (find-spec-refs context check))
            (find-spec-refs context then)))

    (= 'if-value-let (first expr))
    (let [[_ [binding-symbol binding-expr] then else] expr
          sub-result (find-spec-refs context binding-expr)]
      (into (find-spec-refs context else)
            (unwrap-tail-syms [[binding-symbol binding-expr]] {binding-symbol (unwrap-tail-set sub-result)}
                              #{}
                              (find-spec-refs (assoc context binding-symbol sub-result) then))))

    (= 'when-value-let (first expr))
    (let [[_ [binding-symbol binding-expr] then] expr
          sub-result (find-spec-refs context binding-expr)]
      (unwrap-tail-syms [[binding-symbol binding-expr]] {binding-symbol (unwrap-tail-set sub-result)}
                        #{}
                        (find-spec-refs (assoc context binding-symbol sub-result) then)))

    (= 'reduce (first expr))
    (let [[_ [binding-symbol binding-expr] [binding-symbol-2 binding-expr-2] body] expr
          sub-result-1 (find-spec-refs context binding-expr)
          sub-result-2 (find-spec-refs context binding-expr-2)
          sub-results-map {binding-symbol (unwrap-tail-set sub-result-1)}
          binding-pairs [[binding-symbol binding-expr]]]
      (into (unwrap-tail-set sub-result-2)
            (unwrap-tail-syms binding-pairs sub-results-map #{binding-symbol-2}
                              (find-spec-refs (assoc context
                                                     binding-symbol sub-result-1
                                                     binding-symbol-2 sub-result-2)
                                              body))))

    (= 'refine-to (first expr))
    (let [[_ instance-expr spec] expr]
      (conj (unwrap-tail-set (find-spec-refs context instance-expr))
            (wrap-tail spec)))

    (= 'refines-to? (first expr))
    (let [[_ instance-expr spec] expr]
      (conj (unwrap-tail-set (find-spec-refs context instance-expr))
            spec))

    (= #{'any? 'every? 'filter 'map 'sort-by} (first expr))
    (let [[_ [binding-symbol binding-expr] body] expr
          sub-result (find-spec-refs context binding-expr)]
      (into (unwrap-tail-set sub-result)
            (find-spec-refs (assoc context binding-symbol sub-result) body)))

    :default
    (->> (find-collection-spec-refs context (rest expr))
         (map unwrap-tail)
         set)))

(s/defn find-spec-refs :- (s/maybe #{(s/conditional
                                      map? TailSpecRef
                                      :else types/NamespacedKeyword)})
  "Return a set of spec-ids for specs that are referenced by the expr. If a spec-id is referenced in a
  tail position then wrap it in a map that indicates this."
  ([expr]
   (find-spec-refs {} expr))
  ([context expr]
   (cond
     (boolean? expr) #{}
     (base/integer-or-long? expr) #{}
     (base/fixed-decimal? expr) #{}
     (string? expr) #{}
     (symbol? expr) (set (map (partial wrap-sym expr) (get context expr)))
     (keyword? expr) (if (types/namespaced-keyword? expr)
                       #{(wrap-tail expr)}
                       #{})
     (map? expr) (into #{(first (find-spec-refs context (:$type expr)))}
                       (find-collection-spec-refs context (-> expr (dissoc :$type) vals)))
     (seq? expr) (find-seq-spec-refs context expr)
     (set? expr) (find-collection-spec-refs context expr)
     (vector? expr) (find-collection-spec-refs context expr)
     :default (throw (ex-info "unexpected expr to find-spec-refs" {:expr expr})))))

(defn find-spec-refs-but-tail
  [spec-id expr]
  (->> (disj (find-spec-refs {} expr) (wrap-tail spec-id))
       (map unwrap-tail)
       set))

(defn find-spec-refs-including-tail
  [expr]
  (->> (find-spec-refs {} expr)
       (map unwrap-tail)
       set))

;;;;

(s/defn ^:private get-field-dependencies :- (s/maybe {types/NamespacedKeyword #{types/NamespacedKeyword}})
  [spec-map :- envs/SpecMap
   spec-id :- types/NamespacedKeyword
   [_ var-type]]
  (let [ts (->> var-type
                types/innermost-types
                (map types/inner-spec-type)
                (remove nil?)
                set)]
    (when-not (empty? ts)
      {spec-id ts})))

(s/defn ^:private get-constraint-dependencies :- {types/NamespacedKeyword #{types/NamespacedKeyword}}
  [spec-id :- types/NamespacedKeyword
   [_ expr]]
  {spec-id (unwrap-tail-set (find-spec-refs expr))})

(s/defn ^:private get-refines-to-dependencies :- {types/NamespacedKeyword #{types/NamespacedKeyword}}
  [spec-id :- types/NamespacedKeyword
   [other-spec-id {:keys [expr]}]]
  (let [spec-refs (unwrap-tail-set (find-spec-refs expr))]
    {spec-id (into #{other-spec-id} spec-refs)}))

(s/defn ^:private get-spec-dependencies :- {types/NamespacedKeyword #{types/NamespacedKeyword}}
  [spec-map :- envs/SpecMap
   spec-id :- types/NamespacedKeyword
   spec-info :- envs/SpecInfo]
  (let [{:keys [fields constraints refines-to]} spec-info]
    (->> [(map (partial get-field-dependencies spec-map spec-id) fields)
          (map (partial get-constraint-dependencies spec-id) constraints)
          (map (partial get-refines-to-dependencies spec-id) refines-to)]
         (remove nil?)
         (reduce into [])
         (reduce (partial merge-with into) {}))))

(s/defn get-spec-map-dependencies :- {types/NamespacedKeyword #{types/NamespacedKeyword}}
  [spec-map :- envs/SpecMap]
  (->> spec-map
       (map (fn [[k v]] (get-spec-dependencies spec-map k v)))
       (reduce (partial merge-with into) {})))

(s/defn find-cycle-in-dependencies
  [spec-map :- envs/SpecMap]
  (let [spec-map-dependencies (->> spec-map
                                   get-spec-map-dependencies)]
    (if (empty? spec-map-dependencies)
      nil
      (try
        (graph/detect-cycle (keys spec-map-dependencies) spec-map-dependencies)
        nil
        (catch ExceptionInfo ex
          (:path (ex-data ex)))))))

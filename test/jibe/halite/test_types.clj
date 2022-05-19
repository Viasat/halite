;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.test-types
  (:require [jibe.halite.types :as halite-types]
            [clojure.test :as t :refer [deftest is are]]
            [schema.test :refer [validate-schemas]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]))

(clojure.test/use-fixtures :once validate-schemas)

(deftest type-ptn
  (let [T 'T]
    (are [t tp] (= tp (into {} (halite-types/type-ptn t)))
      :Integer                 {:maybe? false, :kind :Integer,  :arg nil}
      [:Set :Nothing]          {:maybe? false, :kind :Set,      :arg :Nothing}
      :ws/A                    {:maybe? false, :kind :Instance  :arg :ws/A}
      [:Maybe :ws/A]           {:maybe? true,  :kind :Instance  :arg :ws/A}
      [:Vec [:Set :Integer]]   {:maybe? false, :kind :Vec,      :arg [:Set :Integer]}
      [:Vec T]                 {:maybe? false, :kind :Vec,      :arg T}
      [:Maybe [:Set :Integer]] {:maybe? true,  :kind :Set,      :arg :Integer}
      [:Maybe [:Set T]]        {:maybe? true,  :kind :Set,      :arg T}
      :Unset                   {:maybe? false, :kind :Unset,    :arg nil}
      :Nothing                 {:maybe? false, :kind :Nothing,  :arg nil})))

(deftest type-ptn-round-trip
  (let [T 'T]
    (are [t] (= t (halite-types/ptn-type (halite-types/type-ptn t)))
      :Integer
      [:Set :Nothing]
      :ws/A
      :Instance
      [:Maybe :ws/A]
      [:Vec [:Set :Integer]]
      [:Vec T]
      [:Maybe [:Set :Integer]]
      [:Maybe [:Set T]]
      :Unset
      [:Coll T]
      [:Maybe [:Coll T]])))

#_(deftest type-nodename
    (are [t tn] (= tn (halite-types/type-nodename t))

      [:Set :Nothing]                [:Set :Nothing]
      :Coll                    :Coll
      :ws/A                    '[:Instance T]
      [:Maybe :ws/A]           '[:Maybe [:Instance T]]
      [:Vec [:Set :Integer]]   '[:Vec T]
      [:Maybe [:Set :Integer]] '[:Maybe [:Set T]]))

(deftest proper-subtypes
  (are [s t] (and (halite-types/subtype? s t) (not (halite-types/subtype? t s)))
    :Boolean [:Maybe :Boolean]
    [:Coll :Integer] [:Maybe [:Coll :Integer]]
    :Instance [:Maybe :Instance]
    :Integer :Any
    :Integer [:Maybe :Integer]
    :Nothing [:Vec [:Coll :String]]
    :ws/A :Instance
    :ws/A [:Maybe :Instance]
    :ws/A [:Maybe :ws/A]
    :Unset [:Maybe [:Vec [:Coll :String]]]
    [:Maybe :ws/A] [:Maybe :Instance]
    [:Maybe :Integer] :Any
    [:Set :Integer] [:Maybe [:Set :Integer]]
    [:Set :Nothing] [:Coll :Object]
    [:Set :Nothing] [:Maybe [:Coll :Object]]
    [:Set :Nothing] [:Set :Integer]
    [:Set :String] :Any
    [:Set :ws/A] [:Coll :ws/A]
    [:Set :ws/A] [:Maybe [:Coll :Object]]
    [:Set :ws/A] [:Maybe [:Set :Instance]]
    [:Vec :String] [:Maybe [:Coll :Object]]
    [:Vec [:Set [:Vec :Integer]]] [:Vec [:Set [:Coll :Object]]]))

(deftest not-subtypes
  (are [s t] (not (halite-types/subtype? s t))
    :Unset :Integer
    :Instance [:Maybe :ws/A]
    [:Set :Integer] [:Maybe [:Set :String]]
    [:Set :Integer] [:Set :String]
    :ws/A [:Maybe :ws/B]
    :ws/A :ws/B))

(deftest meet
  (are [a b m] (= m (halite-types/meet a b) (halite-types/meet b a))
    :String :String :String
    [:Maybe :String] :String [:Maybe :String]
    :Integer :String :Object
    :Integer [:Maybe :Integer] [:Maybe :Integer]
    [:Set :String] [:Vec :String] [:Coll :String]
    [:Set :Nothing] [:Vec :Nothing] [:Coll :Nothing]
    :Unset [:Vec :Nothing] [:Maybe [:Vec :Nothing]]
    [:Vec :Integer] [:Maybe [:Vec :Integer]] [:Maybe [:Vec :Integer]]
    [:Vec :Integer] [:Maybe [:Set :Integer]] [:Maybe [:Coll :Integer]]
    [:Set :Nothing] :Unset [:Maybe [:Set :Nothing]]
    [:Set :Nothing] [:Vec :Integer] [:Coll :Integer]
    [:Vec :String] [:Vec :Integer] [:Vec :Object]
    [:Vec [:Set :Integer]] [:Vec [:Set :Nothing]] [:Vec [:Set :Integer]]
    [:Vec [:Set :Integer]] [:Vec [:Set :String]]  [:Vec [:Set :Object]]
    [:Vec [:Set :Integer]] [:Vec [:Coll :Object]] [:Vec [:Coll :Object]]
    :ws/A :ws/A :ws/A
    :ws/A :ws/B :Instance
    :ws/A :Instance :Instance
    [:Maybe :ws/A] :Instance [:Maybe :Instance]
    [:Maybe [:Vec :Object]] :Nothing [:Maybe [:Vec :Object]]
    [:Maybe [:Set :ws/B]] [:Maybe [:Set :Nothing]] [:Maybe [:Set :ws/B]]
    :ws/B :Nothing :ws/B
    [:Maybe :ws/B] :Nothing [:Maybe :ws/B]
    [:Maybe :ws/B] :ws/B [:Maybe :ws/B]
    [:Maybe :ws/A] :Instance [:Maybe :Instance]
    [:Maybe :ws/A] [:Maybe :Instance] [:Maybe :Instance]
    :Nothing :Unset :Unset))

(deftest join
  (are [a b m] (= m (halite-types/join a b) (halite-types/join b a))
    :String :String :String
    [:Maybe :String] :String :String
    :Object [:Maybe [:Coll :Integer]] [:Coll :Integer]
    [:Maybe [:Vec :Integer]] [:Maybe [:Set :Integer]] :Unset
    [:Coll :Integer] [:Maybe [:Set :Integer]] [:Set :Integer]
    [:Coll :Object] [:Maybe [:Set :Instance]] [:Set :Instance]
    :Integer [:Maybe :String] :Nothing))

(def gen-type
  (let [r (gen/recursive-gen (fn [inner]
                               (gen/one-of [(gen/fmap (fn [i] [:Vec i]) inner)
                                            (gen/fmap (fn [i] [:Set i]) inner)
                                            (gen/fmap (fn [i] [:Coll i]) inner)]))
                             (gen/elements [:Integer :String :Boolean :ws/A :ws/B :ws/C
                                            :Nothing :Instance :Object]))
        s (gen/one-of [r
                       (gen/elements [:Unset :Any])])]
    (gen/one-of [s
                 (gen/fmap (fn [t]
                             (case t
                               (:Unset :Nothing :Any :Object) t
                               [:Maybe t]))
                           s)])))

(defspec prop-ptn-round-trip
  {:num-tests 1000}
  (prop/for-all [t gen-type]
                (= t (halite-types/ptn-type (halite-types/type-ptn t)))))

(defspec prop-subtype-one-way
  {:num-tests 1000}
  (prop/for-all [s gen-type
                 t gen-type]
                (cond
                  (= s t) true
                  (halite-types/subtype? s t) (not (halite-types/subtype? t s))
                  (halite-types/subtype? t s) (not (halite-types/subtype? s t))
                  :else true)))

(defspec prop-meet-subtype
  {:num-tests 1000}
  (prop/for-all [s gen-type
                 t gen-type]
                (let [m (halite-types/meet s t)]
                  (and (halite-types/subtype? s m)
                       (halite-types/subtype? t m)))))

(defn prop-meet-implication [s t o]
  (let [m (halite-types/meet s t)]
    (cond
      (and (halite-types/subtype? o s)
           (halite-types/subtype? o t))
      (halite-types/subtype? o m)

      (and (halite-types/subtype? s o)
           (halite-types/subtype? t o))
      (halite-types/subtype? m o)

      (halite-types/subtype? m o)
      (and (halite-types/subtype? s o)
           (halite-types/subtype? t o))

      (and (halite-types/subtype? s o)
           (halite-types/subtype? o m))
      (not (halite-types/subtype? t o))

      (and (halite-types/subtype? t o)
           (halite-types/subtype? o m))
      (not (halite-types/subtype? s o))

      :else true)))

(def *type-example-seq
  (delay
   (->> @halite-types/*ptn-adjacent-sub
        keys
        (mapcat #(case (:arg %)
                   T [(assoc % :arg :Integer) (assoc % :arg :String)]
                   KW [(assoc % :arg :ws/A) (assoc % :arg :ws/B)]
                   [%]))
        (map halite-types/ptn-type))))

(deftest test-shallow-exhaustive-meet-implication
  (doseq [s @*type-example-seq
          t @*type-example-seq
          o @*type-example-seq]
    (is (prop-meet-implication s t o))))

(defspec test-prop-meet-implication
  {:num-tests 1000
   :reporter-fn (fn [{:keys [type fail]}]
                  (when-let [[s t o] (and (= :shrunk type) fail)]
                    (prn :s s :t t :o o :m (halite-types/meet s t))))}
  (prop/for-all [s gen-type
                 t gen-type
                 o gen-type]
                (prop-meet-implication s t o)))

(defspec prop-join-subtype
  {:num-tests 1000}
  (prop/for-all [s gen-type
                 t gen-type]
                (let [j (halite-types/join s t)]
                  (and (halite-types/subtype? j s)
                       (halite-types/subtype? j t)))))

(defn prop-join-implication [s t o]
  (let [j (halite-types/join s t)]
    (cond
      (and (halite-types/subtype? o s)
           (halite-types/subtype? o t))
      (halite-types/subtype? o j)

      (or (halite-types/subtype? s o)
          (halite-types/subtype? t o))
      (halite-types/subtype? j o)

      (and (halite-types/subtype? j o)
           (halite-types/subtype? o s))
      (not (halite-types/subtype? t o))

      (and (halite-types/subtype? j o)
           (halite-types/subtype? o t))
      (not (halite-types/subtype? s o))

      (halite-types/subtype? o s)
      (and (not (halite-types/subtype? o t))
           (not (halite-types/subtype? o j)))

      (halite-types/subtype? o t)
      (and (not (halite-types/subtype? o s))
           (not (halite-types/subtype? o j)))

      :else true)))

(defspec test-prop-join-implication
  {:num-tests 1000
   :reporter-fn (fn [{:keys [type fail]}]
                  (when-let [[s t o] (and (= :shrunk type) fail)]
                    (prn :s s :t t :o o :j (halite-types/join s t))))}
  (prop/for-all [s gen-type
                 t gen-type
                 o gen-type]
                (prop-join-implication s t o)))

(comment

  (gen/sample gen-type 30)

  :end)

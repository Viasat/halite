;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-types
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer :all]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.viasat.halite.types :as types]
            [schema.core :as s]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :once validate-schemas)

(deftest type-ptn
  (let [T 'T]
    (are [t tp] (= tp (into {} (types/type-ptn t)))
      :Integer                   {:maybe? false, :kind :Integer,  :arg nil, :r2 nil}
      [:Decimal 1]               {:maybe? false, :kind :Decimal,  :arg 1, :r2 nil}
      [:Set :Nothing]            {:maybe? false, :kind :Set,      :arg :Nothing, :r2 nil}
      [:Instance :ws/A]          {:maybe? false, :kind :Instance  :arg :ws/A, :r2 nil}
      [:Maybe [:Instance :ws/A]] {:maybe? true,  :kind :Instance  :arg :ws/A, :r2 nil}
      [:Vec [:Set :Integer]]     {:maybe? false, :kind :Vec,      :arg [:Set :Integer], :r2 nil}
      [:Vec T]                   {:maybe? false, :kind :Vec,      :arg T, :r2 nil}
      [:Maybe [:Set :Integer]]   {:maybe? true,  :kind :Set,      :arg :Integer, :r2 nil}
      [:Maybe [:Decimal 3]]      {:maybe? true,  :kind :Decimal,  :arg 3, :r2 nil}
      [:Maybe [:Set T]]          {:maybe? true,  :kind :Set,      :arg T, :r2 nil}
      :Unset                     {:maybe? false, :kind :Unset,    :arg nil, :r2 nil}
      :Nothing                   {:maybe? false, :kind :Nothing,  :arg nil, :r2 nil}
      :PreInstance               {:maybe? false, :kind :PreInstance, :arg nil, :r2 nil})))

(deftest type-ptn-round-trip
  (let [T 'T]
    (are [t] (= t (types/ptn-type (types/type-ptn t)))
      :Integer
      [:Decimal 2]
      [:Maybe [:Decimal 2]]
      [:Set :Nothing]
      [:Instance :ws/A]
      [:Instance :*]
      [:Instance :* #{:ws/A}]
      [:Instance :ws/B #{:ws/A}]
      [:Maybe [:Instance :ws/A]]
      [:Vec [:Set :Integer]]
      [:Vec T]
      [:Maybe [:Set :Integer]]
      [:Maybe [:Set T]]
      :Unset
      [:Coll T]
      [:Maybe [:Coll T]]
      :PreInstance)))

(deftest test-schema
  (are [t]
       (nil? (s/check types/HaliteType t))
    :Integer
    [:Decimal 1]
    [:Maybe [:Set :Nothing]]
    [:Coll [:Decimal 3]]
    :PreInstance
    [:Maybe :PreInstance]))

(deftest test-not-schema
  (are [t]
       (boolean (s/check types/HaliteType t))
    [:Decimal]
    [:Decimal "1"]
    [:Instance :Z #{:ws/B :ws/C}]
    [:Maybe [:PreInstance]]))

#_(deftest type-nodename
    (are [t tn] (= tn (types/type-nodename t))

      [:Set :Nothing]                [:Set :Nothing]
      :Coll                    :Coll
      [:Instance :ws/A]                    '[:Instance T]
      [:Maybe [:Instance :ws/A]]           '[:Maybe [:Instance T]]
      [:Vec [:Set :Integer]]   '[:Vec T]
      [:Maybe [:Set :Integer]] '[:Maybe [:Set T]]))

(deftest proper-subtypes
  (are [s t] (and (types/subtype? s t) (not (types/subtype? t s)))
    :Boolean [:Maybe :Boolean]
    [:Coll :Integer] [:Maybe [:Coll :Integer]]
    [:Instance :*] [:Maybe [:Instance :*]]
    :Integer :Any
    [:Decimal 1] :Any
    :Integer [:Maybe :Integer]
    [:Decimal 1] [:Maybe [:Decimal 1]]
    :Nothing [:Vec [:Coll :String]]
    [:Instance :ws/A] [:Instance :*]
    [:Instance :ws/A] [:Maybe [:Instance :*]]
    [:Instance :ws/A] [:Maybe [:Instance :ws/A]]
    :Unset [:Maybe [:Vec [:Coll :String]]]
    [:Maybe [:Instance :ws/A]] [:Maybe [:Instance :*]]
    [:Maybe :Integer] :Any
    [:Maybe [:Decimal 1]] :Any
    [:Set :Integer] [:Maybe [:Set :Integer]]
    [:Set :Nothing] [:Coll :Value]
    [:Set :Nothing] [:Maybe [:Coll :Value]]
    [:Set :Nothing] [:Set :Integer]
    [:Set :String] :Any
    [:Set [:Instance :ws/A]] [:Coll [:Instance :ws/A]]
    [:Set [:Instance :ws/A]] [:Maybe [:Coll :Value]]
    [:Set [:Instance :ws/A]] [:Maybe [:Set [:Instance :*]]]
    [:Vec :String] [:Maybe [:Coll :Value]]
    [:Vec [:Set [:Vec :Integer]]] [:Vec [:Set [:Coll :Value]]]
    [:Vec [:Set [:Vec [:Decimal 4]]]] [:Vec [:Set [:Coll :Value]]]
    [:Instance :ws/C #{:ws/A :ws/B}] [:Instance :* #{:ws/A}]
    :PreInstance [:Instance :* #{:ws/A}]
    [:Maybe :PreInstance] [:Maybe [:Instance :* #{:ws/A}]]))

(deftest not-subtypes
  (are [s t] (not (types/subtype? s t))
    :Unset :Integer
    :Unset [:Decimal 2]
    [:Decimal 1] [:Decimal 2]
    [:Instance :*] [:Instance :ws/A]
    [:Set :Integer] [:Maybe [:Set :String]]
    [:Set :Integer] [:Set :String]
    [:Set :Integer] [:Set [:Decimal 1]]
    [:Instance :ws/A] [:Maybe [:Instance :ws/B]]
    [:Instance :ws/A] [:Instance :ws/B]
    [:Instance :* #{:ws/A :ws/B}] [:Instance :ws/D #{:ws/C}]
    :PreInstance :Boolean
    [:Maybe :PreInstance] :Boolean))

(deftest meet
  (are [a b m] (= m (types/meet a b) (types/meet b a))
    :String :String :String
    [:Maybe :String] :String [:Maybe :String]
    :Integer :String :Value
    [:Decimal 3] [:Decimal 3] [:Decimal 3]
    [:Decimal 3] :String :Value
    [:Decimal 3] [:Decimal 2] :Value
    [:Decimal 3] [:Maybe [:Decimal 2]] :Any
    [:Decimal 2] [:Maybe [:Decimal 2]] [:Maybe [:Decimal 2]]
    :Integer [:Maybe :Integer] [:Maybe :Integer]
    [:Set :String] [:Vec :String] [:Coll :String]
    [:Set :Nothing] [:Vec :Nothing] [:Coll :Nothing]
    :Unset [:Vec :Nothing] [:Maybe [:Vec :Nothing]]
    [:Vec :Integer] [:Maybe [:Vec :Integer]] [:Maybe [:Vec :Integer]]
    [:Vec :Integer] [:Maybe [:Set :Integer]] [:Maybe [:Coll :Integer]]
    [:Vec [:Decimal 9]] [:Maybe [:Vec [:Decimal 9]]] [:Maybe [:Vec [:Decimal 9]]]
    [:Set :Nothing] :Unset [:Maybe [:Set :Nothing]]
    [:Set :Nothing] [:Vec :Integer] [:Coll :Integer]
    [:Vec :String] [:Vec :Integer] [:Vec :Value]
    [:Vec [:Set :Integer]] [:Vec [:Set :Nothing]] [:Vec [:Set :Integer]]
    [:Vec [:Set :Integer]] [:Vec [:Set :String]]  [:Vec [:Set :Value]]
    [:Vec [:Set :Integer]] [:Vec [:Coll :Value]] [:Vec [:Coll :Value]]
    [:Vec [:Set [:Decimal 2]]] [:Vec [:Set :Nothing]] [:Vec [:Set [:Decimal 2]]]
    [:Instance :ws/A] [:Instance :ws/A] [:Instance :ws/A]
    [:Instance :ws/A] [:Instance :ws/B] [:Instance :*]
    [:Instance :ws/A] [:Instance :*] [:Instance :*]
    [:Maybe [:Instance :ws/A]] [:Instance :*] [:Maybe [:Instance :*]]
    [:Maybe [:Vec :Value]] :Nothing [:Maybe [:Vec :Value]]
    [:Maybe [:Set [:Instance :ws/B]]] [:Maybe [:Set :Nothing]] [:Maybe [:Set [:Instance :ws/B]]]
    [:Instance :ws/B] :Nothing [:Instance :ws/B]
    [:Maybe [:Instance :ws/B]] :Nothing [:Maybe [:Instance :ws/B]]
    [:Maybe [:Instance :ws/B]] [:Instance :ws/B] [:Maybe [:Instance :ws/B]]
    [:Maybe [:Instance :ws/A]] [:Instance :*] [:Maybe [:Instance :*]]
    [:Maybe [:Instance :ws/A]] [:Maybe [:Instance :*]] [:Maybe [:Instance :*]]
    :Nothing :Unset :Unset
    [:Instance :* #{:ws/A :ws/B}] [:Instance :* #{:ws/B :ws/C}] [:Instance :* #{:ws/B}]
    :Nothing [:Instance :* #{:ws/A :ws/B}] [:Instance :* #{:ws/A :ws/B}]
    :Nothing :PreInstance :PreInstance
    :PreInstance :Boolean :Value
    :PreInstance [:Maybe [:Instance :* #{:ws/A :ws/B}]] [:Maybe [:Instance :* #{:ws/A :ws/B}]]
    [:Instance :ws/A] [:Instance :*] [:Instance :*]
    :Unset :PreInstance [:Maybe :PreInstance]
    :Unset [:Instance :*] [:Maybe [:Instance :*]]
    :PreInstance [:Maybe :PreInstance] [:Maybe :PreInstance]
    [:Maybe :PreInstance] [:Maybe [:Instance :ws/A]] [:Maybe [:Instance :ws/A]]))

(deftest join
  (are [a b m] (= m (types/join a b) (types/join b a))
    :String :String :String
    [:Maybe :String] :String :String
    :Value [:Maybe [:Coll :Integer]] [:Coll :Integer]
    :Value [:Maybe [:Coll [:Decimal 1]]] [:Coll [:Decimal 1]]
    [:Maybe [:Vec :Integer]] [:Maybe [:Set :Integer]] :Unset
    [:Coll :Integer] [:Maybe [:Set :Integer]] [:Set :Integer]
    [:Coll :Value] [:Maybe [:Set [:Instance :*]]] [:Set [:Instance :*]]
    :Integer [:Maybe :String] :Nothing
    [:Decimal 2] [:Decimal 2] [:Decimal 2]
    [:Decimal 2] [:Maybe :String] :Nothing
    [:Decimal 2] [:Decimal 1] :Nothing
    [:Decimal 2] [:Maybe [:Decimal 1]] :Nothing
    [:Decimal 2] [:Maybe [:Decimal 2]] [:Decimal 2]
    [:Maybe [:Decimal 1]] [:Maybe [:Decimal 2]] :Unset
    [:Instance :* #{:ws/A :ws/B}] [:Instance :* #{:ws/B :ws/C}] [:Instance :* #{:ws/A :ws/B :ws/C}]
    :Nothing [:Instance :* #{:ws/A :ws/B}] :Nothing
    :Nothing :PreInstance :Nothing
    [:Instance :ws/A] [:Instance :ws/B] :PreInstance
    [:Instance :ws/A] [:Instance :*] [:Instance :ws/A]
    [:Instance :ws/A] [:Instance :* #{:ws/B :ws/C}] [:Instance :ws/A #{:ws/B :ws/C}]
    [:Instance :ws/A] [:Maybe :PreInstance] :PreInstance
    :Unset [:Maybe :PreInstance] :Unset))

(def gen-type
  (let [r (gen/recursive-gen (fn [inner]
                               (gen/one-of [(gen/fmap (fn [i] [:Vec i]) inner)
                                            (gen/fmap (fn [i] [:Set i]) inner)
                                            (gen/fmap (fn [i] [:Coll i]) inner)]))
                             (gen/one-of [(gen/elements [:Integer :String :Boolean :Nothing :Value :PreInstance])
                                          (gen/fmap (fn [[a r2]] [:Instance a (disj r2 a)])
                                                    (gen/tuple (gen/elements [:* :ws/A :ws/B :ws/C])
                                                               (gen/set (gen/elements [:ws/A :ws/B :ws/C]))))
                                          (gen/fmap (fn [[a]] [:Decimal a])
                                                    (gen/tuple (gen/elements [1 2 3])))]))
        s (gen/one-of [r
                       (gen/elements [:Unset :Any])])]
    (gen/one-of [s
                 (gen/fmap (fn [t]
                             (case t
                               (:Unset :Nothing :Any :Value) t
                               [:Maybe t]))
                           s)])))

(defspec prop-ptn-round-trip
  {:num-tests 1001
   :reporter-fn (fn [_])}
  (prop/for-all [t gen-type]
                (= t (types/ptn-type (types/type-ptn t)))))

(defspec prop-subtype-one-way
  {:num-tests 1002
   :reporter-fn (fn [_])}
  (prop/for-all [s gen-type
                 t gen-type]
                (cond
                  (types/types-equivalent? s t) true
                  (types/subtype? s t) (not (types/subtype? t s))
                  (types/subtype? t s) (not (types/subtype? s t))
                  :else true)))

(deftest test-types-equivalent?
  (is (types/types-equivalent?
       [:Maybe [:Coll [:Instance :ws/B #{}]]]
       [:Maybe [:Coll [:Instance :ws/B #{:ws/A}]]]))
  (is (types/types-equivalent?
       [:Instance :ws/B #{}]
       [:Instance :ws/B #{:ws/A}])))

(defspec prop-meet-subtype
  {:num-tests 1003
   :reporter-fn (fn [_])}
  (prop/for-all [s gen-type
                 t gen-type]
                (let [m (types/meet s t)]
                  (and (types/subtype? s m)
                       (types/subtype? t m)))))

(defn prop-meet-implication [s t o]
  (let [m (types/meet s t)]
    (cond
      (and (types/subtype? o s)
           (types/subtype? o t))
      (types/subtype? o m)

      (and (types/subtype? s o)
           (types/subtype? t o))
      (types/subtype? m o)

      (types/subtype? m o)
      (and (types/subtype? s o)
           (types/subtype? t o))

      (and (types/subtype? s o)
           (types/subtype? o m))
      (not (types/subtype? t o))

      (and (types/subtype? t o)
           (types/subtype? o m))
      (not (types/subtype? s o))

      :else true)))

(def *type-example-seq
  (delay
   (->> @types/*ptn-adjacent-sub
        keys
        (mapcat #(case (:arg %)
                   T [(assoc % :arg :Integer) (assoc % :arg :String)]
                   KW (mapcat (fn [r2] [(assoc % :arg :ws/A :r2 r2)
                                        (assoc % :arg :* :r2 r2)])
                              [nil #{:ws/B}])
                   S [(assoc % :arg 1) (assoc % :arg 2)]
                   [%]))
        (map types/ptn-type))))

(deftest test-shallow-exhaustive-meet-implication
  (doseq [s @*type-example-seq
          t @*type-example-seq
          o @*type-example-seq]
    (is (prop-meet-implication s t o))))

(defspec test-prop-meet-implication
  {:num-tests 1004
   :reporter-fn (fn [{:keys [type fail]}]
                  (when-let [[s t o] (and (= :shrunk type) fail)]
                    (prn :s s :t t :o o :m (types/meet s t))))}
  (prop/for-all [s gen-type
                 t gen-type
                 o gen-type]
                (prop-meet-implication s t o)))

(defspec test-prop-join-subtype
  {:num-tests 1005
   :reporter-fn (fn [_])}
  (prop/for-all [s gen-type
                 t gen-type]
                (try
                  (let [j (types/join s t)]
                    (and (types/subtype? j s)
                         (types/subtype? j t)))
                  (catch Exception ex false))))

(defn prop-join-implication [s t o]
  (let [j (types/join s t)]
    (cond
      (and (types/subtype? o s)
           (types/subtype? o t))
      (types/subtype? o j)

      (or (types/subtype? s o)
          (types/subtype? t o))
      (types/subtype? j o)

      (and (types/subtype? j o)
           (types/subtype? o s))
      (not (types/subtype? t o))

      (and (types/subtype? j o)
           (types/subtype? o t))
      (not (types/subtype? s o))

      (types/subtype? o s)
      (and (not (types/subtype? o t))
           (not (types/subtype? o j)))

      (types/subtype? o t)
      (and (not (types/subtype? o s))
           (not (types/subtype? o j)))

      :else true)))

(defspec test-prop-join-implication
  {:num-tests 1006
   :reporter-fn (fn [{:keys [type fail]}]
                  (when-let [[s t o] (and (= :shrunk type) fail)]
                    (prn :s s :t t :o o :j (types/join s t))))}
  (prop/for-all [s gen-type
                 t gen-type
                 o gen-type]
                (prop-join-implication s t o)))

(comment

  (gen/sample gen-type 30)

  :end)

;;;;

(deftest test-refines-to
  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}))
  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/C}}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/D}}))
  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/D}}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/C}}))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/B}}
                                 {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/B}}))

  (is (not (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                      {:maybe? false :kind :Instance :arg :ws/B})))
  (is (not (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/B}
                                      {:maybe? false :kind :Instance :arg :ws/A})))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                 {:maybe? false :kind :Instance :arg :ws/A}))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}))
  (is (not (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                      {:maybe? false :kind :Instance :arg :ws/A})))

  (is (not (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                      {:maybe? false :kind :Instance :arg :ws/A})))
  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}}
                                 {:maybe? false :kind :Instance :arg :ws/A}))
  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                 {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}}))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}))
  (is (not (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}
                                      {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}})))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}))
  (is (not (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}
                                      {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}})))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}))
  (is (not (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}
                                      {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}})))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}))
  (is (not (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}
                                      {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}})))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}))
  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                 {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}))
  (is (not (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}
                                      {:maybe? false :kind :Instance :arg :ws/A})))

  (is (not (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                      {:maybe? false :kind :Instance :arg :ws/B :r2 #{:ws/C}})))
  (is (not (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/B :r2 #{:ws/C}}
                                      {:maybe? false :kind :Instance :arg :ws/A})))

    ;;;;

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                 {:maybe? true :kind :Instance :arg :ws/A}))
  (is (not (#'types/instance-subtype? {:maybe? true :kind :Instance :arg :ws/A}
                                      {:maybe? false :kind :Instance :arg :ws/A})))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :*}
                                 {:maybe? true :kind :Instance :arg :*}))
  (is (not (#'types/instance-subtype? {:maybe? true :kind :Instance :arg :*}
                                      {:maybe? false :kind :Instance :arg :*})))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                 {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}))
  (is (not (#'types/instance-subtype? {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}})))

  (is (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                 {:maybe? true :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}))
  (is (not (#'types/instance-subtype? {:maybe? true :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}
                                      {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}})))

  (is (not (#'types/instance-subtype? {:maybe? true :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}})))
  (is (not (#'types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}
                                      {:maybe? true :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}))))

(deftest test-instance-meet
  (is (= {:maybe? false :kind :Instance :arg :ws/A}
         (#'types/instance-meet {:maybe? false :kind :Instance :arg :ws/A}
                                {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? false :kind :Instance :arg :*}
         (#'types/instance-meet {:maybe? false :kind :Instance :arg :ws/A}
                                {:maybe? false :kind :Instance :arg :ws/B})))
  (is (= {:maybe? false :kind :Instance :arg :*}
         (#'types/instance-meet {:maybe? false :kind :Instance :arg :ws/A}
                                {:maybe? false :kind :Instance :arg :KW})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
         (#'types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}})))
  (is (= {:maybe? false :kind :Instance :arg :*}
         (#'types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
         (#'types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
         (#'types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
         (#'types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B :ws/C}})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
         (#'types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/B}})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
         (#'types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/Z}})))

  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}
         (#'types/instance-meet {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                {:maybe? false :kind :Instance :arg :ws/B :r2 #{:ws/C}})))

  (is (= {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
         (#'types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                {:maybe? true :kind :Instance :arg :ws/A})))
  (is (= {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
         (#'types/instance-meet {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
                                {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
         (#'types/instance-meet {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
                                {:maybe? true :kind :Instance :arg :ws/A})))

  (is (= {:kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
         (#'types/instance-meet {:kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                {:kind :Instance :arg :ws/A :r2 #{:ws/B}})))

  (is (= {:kind :Instance :arg :* :r2 #{:ws/A}}
         (#'types/instance-meet {:kind :Instance :arg :* :r2 #{:ws/A}}
                                {:kind :Instance :arg :ws/A}))))

(deftest test-instance-join
  (is (= {:maybe? false :kind :Instance :arg :ws/A}
         (#'types/instance-join {:maybe? false :kind :Instance :arg :ws/A}
                                {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:kind :PreInstance}
         (#'types/instance-join {:maybe? false :kind :Instance :arg :ws/A}
                                {:maybe? false :kind :Instance :arg :ws/B})))
  (is (= {:maybe? false :kind :Instance :arg :ws/A}
         (#'types/instance-join {:maybe? false :kind :Instance :arg :ws/A}
                                {:maybe? false :kind :Instance :arg :*})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
         (#'types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
         (#'types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}})))
  (is (= {:maybe? false :kind :Instance :arg :ws/A}
         (#'types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}}
         (#'types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B :ws/C}}
         (#'types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B :ws/C}})))
  (is (= {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/B}}
         (#'types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/B}})))
  (is (= {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/B :ws/Z}}
         (#'types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/Z}})))
  (is (= {:kind :PreInstance}
         (#'types/instance-join {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                {:maybe? false :kind :Instance :arg :ws/B :r2 #{:ws/C}})))

  (is (= {:maybe? false :kind :Instance :arg :ws/A}
         (#'types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                {:maybe? true :kind :Instance :arg :ws/A})))
  (is (= {:maybe? false :kind :Instance :arg :ws/A}
         (#'types/instance-join {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
                                {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? true :kind :Instance :arg :ws/A}
         (#'types/instance-join {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
                                {:maybe? true :kind :Instance :arg :ws/A})))
  (is (= {:kind :Unset}
         (#'types/instance-join {:maybe? true :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                {:maybe? true :kind :Instance :arg :ws/B :r2 #{:ws/C}})))
  (is (= {:kind :Unset}
         (#'types/instance-join {:maybe? true :kind :Instance :arg :ws/A}
                                {:maybe? true :kind :Instance :arg :ws/B}))))

(deftest test-instance-helpers
  (is (= [:ws/A false]
         ((juxt #'types/spec-id-ptn #'types/needs-refinement?-ptn)
          {:maybe? true :kind :Instance :arg :ws/A})))
  (is (= [:ws/A false]
         ((juxt #'types/spec-id-ptn #'types/needs-refinement?-ptn)
          {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= [:ws/A true]
         ((juxt #'types/spec-id-ptn #'types/needs-refinement?-ptn)
          {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}})))
  (is (= [:ws/A true]
         ((juxt #'types/spec-id-ptn #'types/needs-refinement?-ptn)
          {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}})))
  (is (= [nil true]
         ((juxt #'types/spec-id-ptn #'types/needs-refinement?-ptn)
          {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A :ws/B}})))
  (is (= [nil true]
         ((juxt #'types/spec-id-ptn #'types/needs-refinement?-ptn)
          {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}})))
  (is (= [nil false]
         ((juxt #'types/spec-id-ptn #'types/needs-refinement?-ptn)
          {:maybe? true :kind :Instance :arg :ws/A :r2 #{:ws/B}})))
  (is (= [nil false]
         ((juxt #'types/spec-id-ptn #'types/needs-refinement?-ptn)
          {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}}))))

;; (time (run-tests))

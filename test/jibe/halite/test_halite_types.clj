;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.test-halite-types
  (:require [jibe.halite.halite-types :as halite-types]
            [clojure.test :as t :refer [deftest is are]]
            [schema.test :refer [validate-schemas]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]))

(clojure.test/use-fixtures :once validate-schemas)

(deftest type-ptn
  (let [T 'T]
    (are [t tp] (= tp (into {} (halite-types/type-ptn t)))
      :Integer                   {:maybe? false, :kind :Integer,  :arg nil, :r2 nil}
      [:Set :Nothing]            {:maybe? false, :kind :Set,      :arg :Nothing, :r2 nil}
      [:Instance :ws/A]          {:maybe? false, :kind :Instance  :arg :ws/A, :r2 nil}
      [:Maybe [:Instance :ws/A]] {:maybe? true,  :kind :Instance  :arg :ws/A, :r2 nil}
      [:Vec [:Set :Integer]]     {:maybe? false, :kind :Vec,      :arg [:Set :Integer], :r2 nil}
      [:Vec T]                   {:maybe? false, :kind :Vec,      :arg T, :r2 nil}
      [:Maybe [:Set :Integer]]   {:maybe? true,  :kind :Set,      :arg :Integer, :r2 nil}
      [:Maybe [:Set T]]          {:maybe? true,  :kind :Set,      :arg T, :r2 nil}
      :Unset                     {:maybe? false, :kind :Unset,    :arg nil, :r2 nil}
      :Nothing                   {:maybe? false, :kind :Nothing,  :arg nil, :r2 nil})))

(deftest type-ptn-round-trip
  (let [T 'T]
    (are [t] (= t (halite-types/ptn-type (halite-types/type-ptn t)))
      :Integer
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
      [:Maybe [:Coll T]])))

#_(deftest type-nodename
    (are [t tn] (= tn (halite-types/type-nodename t))

      [:Set :Nothing]                [:Set :Nothing]
      :Coll                    :Coll
      [:Instance :ws/A]                    '[:Instance T]
      [:Maybe [:Instance :ws/A]]           '[:Maybe [:Instance T]]
      [:Vec [:Set :Integer]]   '[:Vec T]
      [:Maybe [:Set :Integer]] '[:Maybe [:Set T]]))

(deftest proper-subtypes
  (are [s t] (and (halite-types/subtype? s t) (not (halite-types/subtype? t s)))
    :Boolean [:Maybe :Boolean]
    [:Coll :Integer] [:Maybe [:Coll :Integer]]
    [:Instance :*] [:Maybe [:Instance :*]]
    :Integer :Any
    :Integer [:Maybe :Integer]
    :Nothing [:Vec [:Coll :String]]
    [:Instance :ws/A] [:Instance :*]
    [:Instance :ws/A] [:Maybe [:Instance :*]]
    [:Instance :ws/A] [:Maybe [:Instance :ws/A]]
    :Unset [:Maybe [:Vec [:Coll :String]]]
    [:Maybe [:Instance :ws/A]] [:Maybe [:Instance :*]]
    [:Maybe :Integer] :Any
    [:Set :Integer] [:Maybe [:Set :Integer]]
    [:Set :Nothing] [:Coll :Object]
    [:Set :Nothing] [:Maybe [:Coll :Object]]
    [:Set :Nothing] [:Set :Integer]
    [:Set :String] :Any
    [:Set [:Instance :ws/A]] [:Coll [:Instance :ws/A]]
    [:Set [:Instance :ws/A]] [:Maybe [:Coll :Object]]
    [:Set [:Instance :ws/A]] [:Maybe [:Set [:Instance :*]]]
    [:Vec :String] [:Maybe [:Coll :Object]]
    [:Vec [:Set [:Vec :Integer]]] [:Vec [:Set [:Coll :Object]]]
    [:Instance :ws/C #{:ws/A :ws/B}] [:Instance :* #{:ws/A}]))

(deftest not-subtypes
  (are [s t] (not (halite-types/subtype? s t))
    :Unset :Integer
    [:Instance :*] [:Instance :ws/A]
    [:Set :Integer] [:Maybe [:Set :String]]
    [:Set :Integer] [:Set :String]
    [:Instance :ws/A] [:Maybe [:Instance :ws/B]]
    [:Instance :ws/A] [:Instance :ws/B]
    [:Instance :* #{:ws/A :ws/B}] [:Instance :ws/D #{:ws/C}]))

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
    [:Instance :ws/A] [:Instance :ws/A] [:Instance :ws/A]
    [:Instance :ws/A] [:Instance :ws/B] [:Instance :*]
    [:Instance :ws/A] [:Instance :*] [:Instance :*]
    [:Maybe [:Instance :ws/A]] [:Instance :*] [:Maybe [:Instance :*]]
    [:Maybe [:Vec :Object]] :Nothing [:Maybe [:Vec :Object]]
    [:Maybe [:Set [:Instance :ws/B]]] [:Maybe [:Set :Nothing]] [:Maybe [:Set [:Instance :ws/B]]]
    [:Instance :ws/B] :Nothing [:Instance :ws/B]
    [:Maybe [:Instance :ws/B]] :Nothing [:Maybe [:Instance :ws/B]]
    [:Maybe [:Instance :ws/B]] [:Instance :ws/B] [:Maybe [:Instance :ws/B]]
    [:Maybe [:Instance :ws/A]] [:Instance :*] [:Maybe [:Instance :*]]
    [:Maybe [:Instance :ws/A]] [:Maybe [:Instance :*]] [:Maybe [:Instance :*]]
    :Nothing :Unset :Unset
    [:Instance :* #{:ws/A :ws/B}] [:Instance :* #{:ws/B :ws/C}] [:Instance :* #{:ws/B}]))

(deftest join
  (are [a b m] (= m (halite-types/join a b) (halite-types/join b a))
    :String :String :String
    [:Maybe :String] :String :String
    :Object [:Maybe [:Coll :Integer]] [:Coll :Integer]
    [:Maybe [:Vec :Integer]] [:Maybe [:Set :Integer]] :Unset
    [:Coll :Integer] [:Maybe [:Set :Integer]] [:Set :Integer]
    [:Coll :Object] [:Maybe [:Set [:Instance :*]]] [:Set [:Instance :*]]
    :Integer [:Maybe :String] :Nothing
    [:Instance :* #{:ws/A :ws/B}] [:Instance :* #{:ws/B :ws/C}] [:Instance :* #{:ws/A :ws/B :ws/C}]))

(def gen-type
  (let [r (gen/recursive-gen (fn [inner]
                               (gen/one-of [(gen/fmap (fn [i] [:Vec i]) inner)
                                            (gen/fmap (fn [i] [:Set i]) inner)
                                            (gen/fmap (fn [i] [:Coll i]) inner)]))
                             (gen/one-of [(gen/elements [:Integer :String :Boolean :Nothing :Object])
                                          (gen/fmap (fn [[a r2]] [:Instance a (disj r2 a)])
                                                    (gen/tuple (gen/elements [:* :ws/A :ws/B :ws/C])
                                                               (gen/set (gen/elements [:ws/A :ws/B :ws/C]))))]))
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
                  (halite-types/types-equivalent? s t) true
                  (halite-types/subtype? s t) (not (halite-types/subtype? t s))
                  (halite-types/subtype? t s) (not (halite-types/subtype? s t))
                  :else true)))

(deftest test-types-equivalent?
  (is (halite-types/types-equivalent?
       [:Maybe [:Coll [:Instance :ws/B #{}]]]
       [:Maybe [:Coll [:Instance :ws/B #{:ws/A}]]]))
  (is (halite-types/types-equivalent?
       [:Instance :ws/B #{}]
       [:Instance :ws/B #{:ws/A}])))

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
                   KW (mapcat (fn [r2] [(assoc % :arg :ws/A :r2 r2)
                                        (assoc % :arg :* :r2 r2)])
                              [nil #{:ws/B}])
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
                (try
                  (let [j (halite-types/join s t)]
                    (and (halite-types/subtype? j s)
                         (halite-types/subtype? j t)))
                  (catch Exception ex false))))

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

;;;;

(deftest test-refines-to
  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}))
  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/C}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/D}}))
  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/D}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/C}}))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/B}}
                                      {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/B}}))

  (is (not (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                           {:maybe? false :kind :Instance :arg :ws/B})))
  (is (not (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/B}
                                           {:maybe? false :kind :Instance :arg :ws/A})))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                      {:maybe? false :kind :Instance :arg :ws/A}))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}))
  (is (not (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                           {:maybe? false :kind :Instance :arg :ws/A})))

  (is (not (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                           {:maybe? false :kind :Instance :arg :ws/A})))
  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}}
                                      {:maybe? false :kind :Instance :arg :ws/A}))
  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                      {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}}))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}))
  (is (not (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}
                                           {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}})))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}))
  (is (not (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}
                                           {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}})))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}))
  (is (not (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}
                                           {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}})))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}))
  (is (not (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}
                                           {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}})))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}))
  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                      {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}))
  (is (not (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}}
                                           {:maybe? false :kind :Instance :arg :ws/A})))

  (is (not (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                           {:maybe? false :kind :Instance :arg :ws/B :r2 #{:ws/C}})))
  (is (not (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/B :r2 #{:ws/C}}
                                           {:maybe? false :kind :Instance :arg :ws/A})))

    ;;;;

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A}
                                      {:maybe? true :kind :Instance :arg :ws/A}))
  (is (not (halite-types/instance-subtype? {:maybe? true :kind :Instance :arg :ws/A}
                                           {:maybe? false :kind :Instance :arg :ws/A})))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :*}
                                      {:maybe? true :kind :Instance :arg :*}))
  (is (not (halite-types/instance-subtype? {:maybe? true :kind :Instance :arg :*}
                                           {:maybe? false :kind :Instance :arg :*})))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                      {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}))
  (is (not (halite-types/instance-subtype? {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
                                           {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}})))

  (is (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                      {:maybe? true :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}))
  (is (not (halite-types/instance-subtype? {:maybe? true :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}
                                           {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}})))

  (is (not (halite-types/instance-subtype? {:maybe? true :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                           {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}})))
  (is (not (halite-types/instance-subtype? {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}
                                           {:maybe? true :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}))))

(deftest test-instance-meet
  (is (= {:maybe? false :kind :Instance :arg :ws/A}
         (halite-types/instance-meet {:maybe? false :kind :Instance :arg :ws/A}
                                     {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? false :kind :Instance :arg :*}
         (halite-types/instance-meet {:maybe? false :kind :Instance :arg :ws/A}
                                     {:maybe? false :kind :Instance :arg :ws/B})))
  (is (= {:maybe? false :kind :Instance :arg :*}
         (halite-types/instance-meet {:maybe? false :kind :Instance :arg :ws/A}
                                     {:maybe? false :kind :Instance :arg :KW})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
         (halite-types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                     {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}})))
  (is (= {:maybe? false :kind :Instance :arg :*}
         (halite-types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                     {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
         (halite-types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                     {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
         (halite-types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                     {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
         (halite-types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                     {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B :ws/C}})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
         (halite-types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                     {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/B}})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
         (halite-types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                     {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/Z}})))

  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B :ws/C}}
         (halite-types/instance-meet {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                     {:maybe? false :kind :Instance :arg :ws/B :r2 #{:ws/C}})))

  (is (= {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
         (halite-types/instance-meet {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                     {:maybe? true :kind :Instance :arg :ws/A})))
  (is (= {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
         (halite-types/instance-meet {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
                                     {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
         (halite-types/instance-meet {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
                                     {:maybe? true :kind :Instance :arg :ws/A})))

  (is (= {:kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
         (halite-types/instance-meet {:kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                     {:kind :Instance :arg :ws/A :r2 #{:ws/B}})))

  (is (= {:kind :Instance :arg :* :r2 #{:ws/A}}
         (halite-types/instance-meet {:kind :Instance :arg :* :r2 #{:ws/A}}
                                     {:kind :Instance :arg :ws/A}))))

(deftest test-instance-join
  (is (= {:maybe? false :kind :Instance :arg :ws/A}
         (halite-types/instance-join {:maybe? false :kind :Instance :arg :ws/A}
                                     {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:kind :Nothing}
         (halite-types/instance-join {:maybe? false :kind :Instance :arg :ws/A}
                                     {:maybe? false :kind :Instance :arg :ws/B})))
  (is (= {:maybe? false :kind :Instance :arg :ws/A}
         (halite-types/instance-join {:maybe? false :kind :Instance :arg :ws/A}
                                     {:maybe? false :kind :Instance :arg :*})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
         (halite-types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                     {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
         (halite-types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                     {:maybe? false :kind :Instance :arg :* :r2 #{:ws/B}})))
  (is (= {:maybe? false :kind :Instance :arg :ws/A}
         (halite-types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                     {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}}
         (halite-types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                     {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B :ws/C}}
         (halite-types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                     {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B :ws/C}})))
  (is (= {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/B}}
         (halite-types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                     {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/B}})))
  (is (= {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/B :ws/Z}}
         (halite-types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}}
                                     {:maybe? false :kind :Instance :arg :ws/C :r2 #{:ws/A :ws/Z}})))
  (is (= {:kind :Nothing}
         (halite-types/instance-join {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                     {:maybe? false :kind :Instance :arg :ws/B :r2 #{:ws/C}})))

  (is (= {:maybe? false :kind :Instance :arg :ws/A}
         (halite-types/instance-join {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}}
                                     {:maybe? true :kind :Instance :arg :ws/A})))
  (is (= {:maybe? false :kind :Instance :arg :ws/A}
         (halite-types/instance-join {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
                                     {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= {:maybe? true :kind :Instance :arg :ws/A}
         (halite-types/instance-join {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}}
                                     {:maybe? true :kind :Instance :arg :ws/A})))
  (is (= {:kind :Unset}
         (halite-types/instance-join {:maybe? true :kind :Instance :arg :ws/A :r2 #{:ws/B :ws/C}}
                                     {:maybe? true :kind :Instance :arg :ws/B :r2 #{:ws/C}})))
  (is (= {:kind :Unset}
         (halite-types/instance-join {:maybe? true :kind :Instance :arg :ws/A}
                                     {:maybe? true :kind :Instance :arg :ws/B}))))

(deftest test-instance-helpers
  (is (= [:ws/A false]
         ((juxt #'halite-types/instance-spec-id-ptn #'halite-types/instance-needs-refinement?-ptn)
          {:maybe? true :kind :Instance :arg :ws/A})))
  (is (= [:ws/A false]
         ((juxt #'halite-types/instance-spec-id-ptn #'halite-types/instance-needs-refinement?-ptn)
          {:maybe? false :kind :Instance :arg :ws/A})))
  (is (= [:ws/A true]
         ((juxt #'halite-types/instance-spec-id-ptn #'halite-types/instance-needs-refinement?-ptn)
          {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A}})))
  (is (= [:ws/A true]
         ((juxt #'halite-types/instance-spec-id-ptn #'halite-types/instance-needs-refinement?-ptn)
          {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A}})))
  (is (= [nil true]
         ((juxt #'halite-types/instance-spec-id-ptn #'halite-types/instance-needs-refinement?-ptn)
          {:maybe? true :kind :Instance :arg :* :r2 #{:ws/A :ws/B}})))
  (is (= [nil true]
         ((juxt #'halite-types/instance-spec-id-ptn #'halite-types/instance-needs-refinement?-ptn)
          {:maybe? false :kind :Instance :arg :* :r2 #{:ws/A :ws/B}})))
  (is (= [nil false]
         ((juxt #'halite-types/instance-spec-id-ptn #'halite-types/instance-needs-refinement?-ptn)
          {:maybe? true :kind :Instance :arg :ws/A :r2 #{:ws/B}})))
  (is (= [nil false]
         ((juxt #'halite-types/instance-spec-id-ptn #'halite-types/instance-needs-refinement?-ptn)
          {:maybe? false :kind :Instance :arg :ws/A :r2 #{:ws/B}}))))

;; (time (t/run-tests))

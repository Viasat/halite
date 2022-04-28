;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.propagate
  "Constraint propagation for halite."
  (:require [clojure.string :as str]
            [jibe.halite.envs :as halite-envs]
            [jibe.halite.types :as halite-types]
            [jibe.halite.transpile.to-choco-clj2 :as h2c]
            [schema.core :as s]
            [viasat.choco-clj :as choco-clj]))

(s/defschema ^:private UnflattenedChocoBounds
  {halite-types/BareKeyword (s/cond-pre choco-clj/VarBound (s/recursive #'UnflattenedChocoBounds))})

(s/defn ^:private unflatten-choco-bounds :- UnflattenedChocoBounds
  [choco-bounds :- {[halite-types/BareKeyword] choco-clj/VarBound}]
  (->> choco-bounds
       (group-by (comp first key))
       (map (fn [[var-kw bounds]]
              (let [[var-kws var-bound] (first bounds)
                    bounds (into {} bounds)]
                [var-kw (if (= [var-kw] var-kws)
                          var-bound
                          (-> bounds
                              (update-keys #(drop 1 %))
                              (unflatten-choco-bounds)))])))
       (into {})))

(defn- decompose-var-name [sym]
  (-> sym name (str/split #"\|") (->> (map keyword))))

(s/defn ^:private atom-bound :- h2c/AtomBound
  [choco-bound :- choco-clj/VarBound]
  (if (or (vector? choco-bound) (set? choco-bound))
    {:$in choco-bound}
    choco-bound))

(s/defn ^:private spec-bound* :- h2c/SpecBound
  [senv :- (s/protocol halite-envs/SpecEnv), spec-id :- halite-types/NamespacedKeyword, unflattened-bounds :- UnflattenedChocoBounds]
  (let [{:keys [spec-vars]} (halite-envs/lookup-spec senv spec-id)]
    (reduce
     (fn [bound [var-kw htype]]
       (let [unflattened-bound (unflattened-bounds var-kw)]
         (assoc bound var-kw
                (cond
                  (and (nil? unflattened-bound) (halite-types/spec-type? htype)) {:$type htype}
                  (#{:Integer :Boolean} htype) (atom-bound unflattened-bound)
                  (halite-types/spec-type? htype) (spec-bound* senv htype unflattened-bound)
                  :else (throw (ex-info "BUG! Cannot reconstitute spec bound"
                                        {:spec-id spec-id :unflattened-bound unflattened-bound
                                         :var-kw var-kw :halite-type htype}))))))
     {:$type spec-id}
     spec-vars)))

(s/defn ^:private spec-bound :- h2c/SpecBound
  [senv :- (s/protocol halite-envs/SpecEnv), spec-id :- halite-types/NamespacedKeyword, choco-bounds :- choco-clj/VarBounds]
  (-> choco-bounds
      (update-keys decompose-var-name)
      (unflatten-choco-bounds)
      (->> (spec-bound* senv spec-id))))

(s/defschema Opts
  {:default-int-bounds [(s/one s/Int :lower) (s/one s/Int :upper)]})

(s/defn propagate :- h2c/SpecBound
  [senv :- (s/protocol halite-envs/SpecEnv), opts :- Opts, initial-bound :- h2c/SpecBound]
  (binding [choco-clj/*default-int-bounds* (:default-int-bounds opts)]
    (->> initial-bound
         (h2c/transpile senv)
         (choco-clj/propagate)
         (spec-bound senv (:$type initial-bound)))))

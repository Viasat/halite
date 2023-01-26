;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.h-err
  (:require [com.viasat.halite.lib.format-errors :refer [deferr merge-field-map]]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.format_errors Text]))

(set! *warn-on-reflection* true)

(deferr missing-required-vars [data]
        {:template "Missing required variables: :missing-vars"})

(deferr field-name-not-in-spec [data]
        {:template "Variables not defined on spec: :invalid-vars"})

(deferr resource-spec-not-found [data]
        {:template "Resource spec not found: :spec-id"})

(deferr no-refinement-path [data]
        {:template "No active refinement path from ':type' to ':target-type'"})

(deferr no-abstract [data]
        {:template "Instance cannot contain abstract value"})

(deferr invalid-instance [data]
        {:template "Invalid instance of ':spec-id', violates constraints :violated-constraint-labels"})

(deferr missing-type-field [data]
        {:template "Instance literal must have :$type field"})

(deferr invalid-type-value [data]
        {:template "Expected namespaced keyword as value of :$type"})

(deferr value-of-wrong-type [data]
        {:template "Value of ':sym has wrong type"})

(deferr field-value-of-wrong-type [data]
        {:template "Value of ':variable' has wrong type"})

(deferr invalid-collection-type [data]
        {:template "Collection value is not of a supported type"})

(deferr invalid-value [data]
        {:template "Invalid value"})

(deferr invalid-expression [data]
        {:template "Invalid expression"})

(deferr literal-must-evaluate-to-value [data]
        {:template ":coll-type-string literal element must always evaluate to a value"})

(deferr size-exceeded [data]
        {:template ":object-type size of :actual-count exceeds the max allowed size of :count-limit"})

(deferr limit-exceeded [data]
        {:template ":object-type of :value exceeds the max allowed value of :limit"})

(deferr abs-failure [data]
        {:template "Cannot compute absolute value of: :value"})

(deferr invalid-exponent [data]
        {:template "Invalid exponent: :exponent"})

(deferr spec-threw [data]
        {:template "Spec threw error: :spec-error-str"})

(deferr instance-threw [data]
        {:template "Instance threw error: :instance-error-str"})

(deferr unknown-function-or-operator [data]
        {:template "Unknown function or operator: :op"})

(deferr syntax-error [data]
        {:template "Syntax error"})

(deferr no-matching-signature [data]
        {:template "No matching signature for ':op'"})

(deferr undefined-symbol [data]
        {:template "Undefined: ':form'"})

(deferr wrong-arg-count [data]
        {:template "Wrong number of arguments to ':op': expected :expected-arg-count, but got :actual-arg-count"})

(deferr wrong-arg-count-min [data]
        {:template "Wrong number of arguments to ':op': expected at least :minimum-arg-count, but got :actual-arg-count"})

(deferr wrong-arg-count-odd [data]
        {:template "Wrong number of arguments to ':op': expected odd number of arguments, but got :actual-arg-count"})

(deferr invalid-vector-index [data]
        {:template "Index must be an integer when target is a vector"})

(deferr invalid-instance-index [data]
        {:template "Index must be a variable name (as a keyword) when target is an instance"})

(deferr index-out-of-bounds [data]
        {:template "Index out of bounds, :index, for vector of length :length"})

(deferr invalid-lookup-target [data]
        {:template "Lookup target must be an instance of known type or non-empty vector"})

(deferr arg-type-mismatch [data]
        {:template ":position-text to ':op' must be :expected-type-description"})

(deferr not-both-vectors [data]
        {:template "When first argument to ':op' is a vector, second argument must also be a vector"})

(deferr let-bindings-odd-count [data]
        {:template "Let bindings form must have an even number of forms"})

(deferr let-needs-bare-symbol [data]
        {:template "Even-numbered forms in let binding vector must be bare symbols"})

(deferr cannot-bind-reserved-word [data]
        {:template "Cannot bind a value to the reserved word: :sym"})

(deferr comprehend-binding-wrong-count [data]
        {:template "Binding form for 'op' must have one variable and one collection"})

(deferr binding-target-must-be-bare-symbol [data]
        {:template "Binding target for ':op' must be a bare symbol, not: :sym"})

(deferr element-binding-target-must-be-bare-symbol [data]
        {:template "Element binding target for ':op' must be a bare symbol, not: :element"})

(deferr element-accumulator-same-symbol [data]
        {:template "Cannot use the same symbol for accumulator and element binding: :element"})

(deferr comprehend-collection-invalid-type [data]
        {:template "Collection required for ':op', not :actual-type"})

(deferr not-boolean-body [data]
        {:template "Body expression in ':op' must be boolean"})

(deferr not-boolean-constraint [data]
        {:template "Constraint expression ':expr' must have Boolean type"})

(deferr not-sortable-body [data]
        {:template "Body expression in ':op' must be sortable, not :actual-type"})

(deferr accumulator-target-must-be-bare-symbol [data]
        {:template "Accumulator binding target for ':op' must be a bare symbol, not: :accumulator"})

(deferr reduce-not-vector [data]
        {:template "Second binding expression to 'reduce' must be a vector."})

(deferr if-value-must-be-bare-symbol [data]
        {:template "First argument to ':op' must be a bare symbol"})

(deferr arguments-not-sets [data]
        {:template "Arguments to ':op' must be sets"})

(deferr argument-not-vector [data]
        {:template "Argument to ':op' must be a vector"})

(deferr argument-empty [data]
        {:template "Argument to first is empty"})

(deferr argument-not-set-or-vector [data]
        {:template "First argument to 'conj' must be a set or vector"})

(deferr cannot-conj-unset [data]
        {:template "Cannot conj possibly unset value to :type-string"})

(deferr refinement-error [data]
        {:template "Refinement from ':type' failed unexpectedly: :underlying-error-message"})

(deferr symbols-not-bound [data]
        {:template "Symbols in type environment are not bound: :unbound-symbols"})

(deferr symbol-undefined [data]
        {:template "Symbol ':form' is undefined"})

(deferr invalid-refinement-expression [data]
        {:template "Refinement expression, ':form', is not of the expected type"})

(deferr must-produce-value [data]
        {:template "Expression provided to 'map' must produce a value: :form"})

(deferr get-in-path-must-be-vector-literal [data]
        {:template "The path parameter in 'get-in' must be a vector literal: :form"})

(deferr invalid-symbol-char [data]
        {:template "The symbol contains invalid characters: :form"})

(deferr invalid-symbol-length [data]
        {:template "The symbol is too long"})

(deferr invalid-keyword-char [data]
        {:template "The keyword contains invalid characters: :form"})

(deferr invalid-keyword-length [data]
        {:template "The keyword is too long"})

(deferr sort-value-collision [data]
        {:template "Multiple elements produced the same sort value, so the collection cannot be deterministically sorted"})

(deferr divide-by-zero [data]
        {:template "Cannot divide by zero"})

(deferr overflow [data]
        {:template "Numeric value overflow"})

(deferr spec-cycle-runtime [data]
        {:template "Loop detected in spec dependencies"})

(deferr refinement-diamond [data]
        {:template "Diamond detected in refinement graph"})

(deferr spec-cycle [data]
        {:template "Cycle detected in spec dependencies"})

(deferr spec-map-needed [data]
        {:template "Operation cannot be performed unless a spec-map is provided"})

(deferr unknown-type-collection [data]
        {:template "Collections must contain values of a single known type"})

(deferr invalid-refines-to-bound [data]
        {:template "No such refinement path for $refines-to bounds from spec ':spec-id' to spec ':to-spec-id'."})

(deferr invalid-refines-to-bound-conflict [data]
        {:template "Cannot provide $refines-to bounds for ':spec-id' that is both required and unset"})

(merge-field-map {:actual-arg-count s/Int
                  :actual-count s/Int
                  :coll-type-string s/Symbol
                  :constraint-name String
                  :count-limit s/Int
                  :element (s/conditional symbol? s/Symbol
                                          :else s/Int)
                  :entry-spec-id (s/maybe s/Keyword)
                  :expected-arg-count s/Int
                  :expected-type s/Keyword
                  :expected-type-description Text
                  :exponent s/Int
                  :instruction {s/Any s/Any}
                  :instruction-time String
                  :invalid-vars [s/Symbol]
                  :just-keys [s/Keyword]
                  :last-modified-time String
                  :limit s/Int
                  :minimum-arg-count s/Int
                  :missing-vars [s/Symbol]
                  :object-type (s/conditional symbol? s/Symbol
                                              :else String)
                  :position-text Text
                  :reachable [#{s/Keyword}]
                  :spec-error-str String
                  :spec-id s/Symbol
                  :target-type s/Symbol
                  :to-spec-id s/Symbol
                  :type s/Symbol
                  :type-string s/Symbol
                  :underlying-error-message String
                  :variable s/Symbol
                  :violated-constraints [{:spec-id s/Keyword
                                          :name String}]
                  :violated-constraint-labels [String]
                  :workspace-name s/Symbol})

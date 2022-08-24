;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.h-err
  (:require [jibe.lib.format-errors :refer [deferr merge-field-map]]
            [schema.core :as s])
  (:import [jibe.lib.format_errors Text]))

(set! *warn-on-reflection* true)

(deferr missing-required-vars [data]
        {:message "Missing required variables: :missing-vars"})

(deferr variables-not-in-spec [data]
        {:message "Variables not defined on spec: :invalid-vars"})

(deferr resource-spec-not-found [data]
        {:message "Resource spec not found: :spec-id"})

(deferr no-active-refinement-path [data]
        {:message "No active refinement path from ':type' to ':target-type'"})

(deferr no-abstract [data]
        {:message "Instance cannot contain abstract value"})

(deferr invalid-instance [data]
        {:message "Invalid instance of ':spec-id', violates constraints :violated-constraints"})

(deferr missing-type-field [data]
        {:message "Instance literal must have :$type field"})

(deferr invalid-type-value [data]
        {:message "Expected namespaced keyword as value of :$type"})

(deferr invalid-field-value [data]
        {:message "Value of ':variable' has wrong type"})

(deferr invalid-value-for-context [data]
        {:message "Value of ':sym has wrong type"})

(deferr invalid-value [data]
        {:message "Invalid value"})

(deferr invalid-expression [data]
        {:message "Invalid expression"})

(deferr literal-must-evaluate-to-value [data]
        {:message ":coll-type-string literal element must always evaluate to a value"})

(deferr size-exceeded [data]
        {:message ":object-type size of :actual-count exceeds the max allowed size of :count-limit"})

(deferr limit-exceeded [data]
        {:message ":object-type of :value exceeds the max allowed value of :limit"})

(deferr abs-failure [data]
        {:message "Cannot compute absolute value of: :value"})

(deferr invalid-exponent [data]
        {:message "Invalid exponent: :exponent"})

(deferr spec-threw [data]
        {:message "Spec threw error: :spec-error-str"})

(deferr unknown-function-or-operator [data]
        {:message "Unknown function or operator: :op"})

(deferr syntax-error [data]
        {:message "Syntax error"})

(deferr no-matching-signature [data]
        {:message "No matching signature for ':op'"})

(deferr undefined-symbol [data]
        {:message "Undefined: ':form'"})

(deferr wrong-arg-count [data]
        {:message "Wrong number of arguments to ':op': expected :expected-arg-count, but got :actual-arg-count"})

(deferr wrong-arg-count-min [data]
        {:message "Wrong number of arguments to ':op': expected at least :minimum-arg-count, but got :actual-arg-count"})

(deferr invalid-vector-index [data]
        {:message "Index must be an integer when target is a vector"})

(deferr invalid-instance-index [data]
        {:message "Index must be a variable name (as a keyword) when target is an instance"})

(deferr index-out-of-bounds [data]
        {:message "Index out of bounds, :index, for vector of length :length"})

(deferr invalid-lookup-target [data]
        {:message "Lookup target must be an instance of known type or non-empty vector"})

(deferr arg-type-mismatch [data]
        {:message ":position-text to ':op' must be :expected-type-description"})

(deferr arg-types-both-vectors [data]
        {:message "When first argument to ':op' is a vector, second argument must also be a vector"})

(deferr let-bindings-odd-count [data]
        {:message "Let bindings form must have an even number of forms"})

(deferr let-symbols-required [data]
        {:message "Even-numbered forms in let binding vector must be symbols"})

(deferr cannot-bind-reserved-word [data]
        {:message "Cannot bind a value to the reserved word: :sym"})

(deferr comprehend-binding-wrong-types [data]
        {:message "Binding form for 'op' must have one variable and one collection"})

(deferr binding-target-must-be-symbol [data]
        {:message "Binding target for ':op' must be a bare symbol, not: :sym"})

(deferr element-binding-target-must-be-symbol [data]
        {:message "Element binding target for ':op' must be a bare symbol, not: :element"})

(deferr element-accumulator-same-symbol [data]
        {:message "Cannot use the same symbol for accumulator and element binding: :element"})

(deferr comprehend-collection-invalid-type [data]
        {:message "Collection required for ':op', not :actual-type"})

(deferr not-boolean-body [data]
        {:message "Body expression in ':op' must be boolean"})

(deferr not-boolean-constraint [data]
        {:message "Constraint expression ':expr' must have Boolean type"})

(deferr not-sortable-body [data]
        {:message "Body expression in ':op' must be sortable, not :actual-type"})

(deferr accumulator-target-must-be-bare-symbol [data]
        {:message "Accumulator binding target for ':op' must be a bare symbol, not: :accumulator"})

(deferr reduce-not-vector [data]
        {:message "Second binding expression to 'reduce' must be a vector."})

(deferr if-value-must-be-bare-symbol [data]
        {:message "First argument to ':op' must be a bare symbol"})

(deferr arguments-not-sets [data]
        {:message "Arguments to ':op' must be sets"})

(deferr argument-not-vector [data]
        {:message "Argument to ':op' must be a vector"})

(deferr argument-empty [data]
        {:message "Argument to first is always empty"})

(deferr argument-not-set-or-vector [data]
        {:message "First argument to 'conj' must be a set or vector"})

(deferr cannot-conj-unset [data]
        {:message "Cannot conj possibly unset value to :type-string"})

(deferr refinement-error [data]
        {:message "Refinement from ':type' failed unexpectedly: :underlying-error-message"})

(deferr symbols-not-bound [data]
        {:message "Symbols in type environment are not bound: :unbound-symbols"})

(deferr symbol-undefined [data]
        {:message "Symbol ':form' is undefined"})

(deferr invalid-refinement-expression [data]
        {:message "Invalid refinement expression: :form"})

(deferr must-produce-value [data]
        {:message "Expression provided to 'map' must produce a value: :form"})

(deferr get-in-path-must-be-vector-literal [data]
        {:message "The path parameter in 'get-in' must be a vector literal: :form"})

(deferr invalid-symbol-char [data]
        {:message "The symbol contains invalid characters: :form"})

(deferr invalid-symbol-length [data]
        {:message "The symbol is too long"})

(deferr invalid-keyword-char [data]
        {:message "The keyword contains invalid characters: :form"})

(deferr invalid-keyword-length [data]
        {:message "The keyword is too long"})

(deferr sort-value-collision [data]
        {:message "Multiple elements produced the same sort value, so the collection cannot be deterministically sorted"})

(deferr divide-by-zero [data]
        {:message "Cannot divide by zero"})

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
                  :type s/Symbol
                  :type-string s/Symbol
                  :underlying-error-message String
                  :variable s/Symbol
                  :violated-constraints [s/Symbol]
                  :workspace-name s/Symbol})

;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.h-err
  (:require [com.viasat.halite.lib.format-errors :as format-errors]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.format_errors Text]))

(set! *warn-on-reflection* true)

(format-errors/deferr missing-required-vars [data]
                      {:template "Missing required variables: :missing-vars"})

(format-errors/deferr field-name-not-in-spec [data]
                      {:template "Variables not defined on spec: :invalid-vars"})

(format-errors/deferr resource-spec-not-found [data]
                      {:template "Resource spec not found: :spec-id"})

(format-errors/deferr no-refinement-path [data]
                      {:template "No active refinement path from ':type' to ':target-type'"})

(format-errors/deferr no-abstract [data]
                      {:template "Instance cannot contain abstract value"})

(format-errors/deferr invalid-instance [data]
                      {:template "Invalid instance of ':spec-id', violates constraints :violated-constraint-labels"})

(format-errors/deferr missing-type-field [data]
                      {:template "Instance literal must have :$type field"})

(format-errors/deferr invalid-type-value [data]
                      {:template "Expected namespaced keyword as value of :$type"})

(format-errors/deferr value-of-wrong-type [data]
                      {:template "Value of ':sym has wrong type"})

(format-errors/deferr field-value-of-wrong-type [data]
                      {:template "Value of ':variable' has wrong type"})

(format-errors/deferr invalid-collection-type [data]
                      {:template "Collection value is not of a supported type"})

(format-errors/deferr invalid-value [data]
                      {:template "Invalid value"})

(format-errors/deferr invalid-expression [data]
                      {:template "Invalid expression"})

(format-errors/deferr literal-must-evaluate-to-value [data]
                      {:template ":coll-type-string literal element must always evaluate to a value"})

(format-errors/deferr size-exceeded [data]
                      {:template ":object-type size of :actual-count exceeds the max allowed size of :count-limit"})

(format-errors/deferr limit-exceeded [data]
                      {:template ":object-type of :value exceeds the max allowed value of :limit"})

(format-errors/deferr abs-failure [data]
                      {:template "Cannot compute absolute value of: :value"})

(format-errors/deferr invalid-exponent [data]
                      {:template "Invalid exponent: :exponent"})

(format-errors/deferr spec-threw [data]
                      {:template "Spec threw error: :spec-error-str"})

(format-errors/deferr instance-threw [data]
                      {:template "Instance threw error: :instance-error-str"})

(format-errors/deferr unknown-function-or-operator [data]
                      {:template "Unknown function or operator: :op"})

(format-errors/deferr syntax-error [data]
                      {:template "Syntax error"})

(format-errors/deferr no-matching-signature [data]
                      {:template "No matching signature for ':op'"})

(format-errors/deferr undefined-symbol [data]
                      {:template "Undefined: ':form'"})

(format-errors/deferr wrong-arg-count [data]
                      {:template "Wrong number of arguments to ':op': expected :expected-arg-count, but got :actual-arg-count"})

(format-errors/deferr wrong-arg-count-min [data]
                      {:template "Wrong number of arguments to ':op': expected at least :minimum-arg-count, but got :actual-arg-count"})

(format-errors/deferr wrong-arg-count-odd [data]
                      {:template "Wrong number of arguments to ':op': expected odd number of arguments, but got :actual-arg-count"})

(format-errors/deferr invalid-vector-index [data]
                      {:template "Index must be an integer when target is a vector"})

(format-errors/deferr invalid-instance-index [data]
                      {:template "Index must be a variable name (as a keyword) when target is an instance"})

(format-errors/deferr index-out-of-bounds [data]
                      {:template "Index out of bounds, :index, for vector of length :length"})

(format-errors/deferr invalid-lookup-target [data]
                      {:template "Lookup target must be an instance of known type or non-empty vector"})

(format-errors/deferr arg-type-mismatch [data]
                      {:template ":position-text to ':op' must be :expected-type-description"})

(format-errors/deferr not-both-vectors [data]
                      {:template "When first argument to ':op' is a vector, second argument must also be a vector"})

(format-errors/deferr let-bindings-odd-count [data]
                      {:template "Let bindings form must have an even number of forms"})

(format-errors/deferr let-needs-bare-symbol [data]
                      {:template "Even-numbered forms in let binding vector must be bare symbols"})

(format-errors/deferr cannot-bind-reserved-word [data]
                      {:template "Cannot bind a value to the reserved word: :sym"})

(format-errors/deferr comprehend-binding-wrong-count [data]
                      {:template "Binding form for 'op' must have one variable and one collection"})

(format-errors/deferr binding-target-must-be-bare-symbol [data]
                      {:template "Binding target for ':op' must be a bare symbol, not: :sym"})

(format-errors/deferr element-binding-target-must-be-bare-symbol [data]
                      {:template "Element binding target for ':op' must be a bare symbol, not: :element"})

(format-errors/deferr element-accumulator-same-symbol [data]
                      {:template "Cannot use the same symbol for accumulator and element binding: :element"})

(format-errors/deferr comprehend-collection-invalid-type [data]
                      {:template "Collection required for ':op', not :actual-type"})

(format-errors/deferr not-boolean-body [data]
                      {:template "Body expression in ':op' must be boolean"})

(format-errors/deferr not-boolean-constraint [data]
                      {:template "Constraint expression ':expr' must have Boolean type"})

(format-errors/deferr not-sortable-body [data]
                      {:template "Body expression in ':op' must be sortable, not :actual-type"})

(format-errors/deferr accumulator-target-must-be-bare-symbol [data]
                      {:template "Accumulator binding target for ':op' must be a bare symbol, not: :accumulator"})

(format-errors/deferr reduce-not-vector [data]
                      {:template "Second binding expression to 'reduce' must be a vector."})

(format-errors/deferr if-value-must-be-bare-symbol [data]
                      {:template "First argument to ':op' must be a bare symbol"})

(format-errors/deferr arguments-not-sets [data]
                      {:template "Arguments to ':op' must be sets"})

(format-errors/deferr argument-not-vector [data]
                      {:template "Argument to ':op' must be a vector"})

(format-errors/deferr argument-not-collection [data]
                      {:template "Argument to ':op' must be a collection"})

(format-errors/deferr not-set-with-single-value [data]
                      {:template "The first item cannot be obtained from a set that contains multiple values."})

(format-errors/deferr argument-empty [data]
                      {:template "Argument to first is empty"})

(format-errors/deferr argument-not-set-or-vector [data]
                      {:template "First argument to 'conj' must be a set or vector"})

(format-errors/deferr cannot-conj-unset [data]
                      {:template "Cannot conj possibly unset value to :type-string"})

(format-errors/deferr refinement-error [data]
                      {:template "Refinement from ':type' failed unexpectedly: :underlying-error-message"})

(format-errors/deferr symbols-not-bound [data]
                      {:template "Symbols in type environment are not bound: :unbound-symbols"})

(format-errors/deferr symbol-undefined [data]
                      {:template "Symbol ':form' is undefined"})

(format-errors/deferr invalid-refinement-expression [data]
                      {:template "Refinement expression, ':form', is not of the expected type"})

(format-errors/deferr must-produce-value [data]
                      {:template "Expression provided to 'map' must produce a value: :form"})

(format-errors/deferr get-in-path-must-be-vector-literal [data]
                      {:template "The path parameter in 'get-in' must be a vector literal: :form"})

(format-errors/deferr invalid-symbol-char [data]
                      {:template "The symbol contains invalid characters: :form"})

(format-errors/deferr invalid-symbol-length [data]
                      {:template "The symbol is too long"})

(format-errors/deferr invalid-keyword-char [data]
                      {:template "The keyword contains invalid characters: :form"})

(format-errors/deferr invalid-keyword-length [data]
                      {:template "The keyword is too long"})

(format-errors/deferr sort-value-collision [data]
                      {:template "Multiple elements produced the same sort value, so the collection cannot be deterministically sorted"})

(format-errors/deferr divide-by-zero [data]
                      {:template "Cannot divide by zero"})

(format-errors/deferr overflow [data]
                      {:template "Numeric value overflow"})

(format-errors/deferr spec-cycle-runtime [data]
                      {:template "Loop detected in spec dependencies"})

(format-errors/deferr refinement-diamond [data]
                      {:template "Diamond detected in refinement graph"})

(format-errors/deferr spec-cycle [data]
                      {:template "Cycle detected in spec dependencies"})

(format-errors/deferr spec-map-needed [data]
                      {:template "Operation cannot be performed unless a spec-map is provided"})

(format-errors/deferr unknown-type-collection [data]
                      {:template "Collections must contain values of a single known type"})

(format-errors/deferr invalid-refines-to-bound [data]
                      {:template "No such refinement path for $refines-to bounds from spec ':spec-id' to spec ':to-spec-id'."})

(format-errors/deferr invalid-refines-to-bound-conflict [data]
                      {:template "Cannot provide $refines-to bounds for ':spec-id' that is both required and unset"})

(format-errors/deferr no-valid-instance-in-bound [data]
                      {:template "No valid instance exists within the given initial-bound: ':initial-bound'"})

(format-errors/merge-field-map {:actual-arg-count s/Int
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

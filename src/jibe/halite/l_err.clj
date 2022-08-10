;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.l-err
  (:require [jibe.lib.format-errors :refer [deferr]]))

(set! *warn-on-reflection* true)

(deferr function-not-found [data]
        {:message "Function ':op' not found"})

(deferr disallowed-nothing [data]
        {:message "Disallowed ':Nothing' expression: :nothing-arg"})

(deferr no-matching-signature [data]
        {:message "No matching signature for ':op'"})

(deferr undefined [data]
        {:message "Undefined: ':form'"})

(deferr undefined-use-of-unset-variable [data]
        {:message "Disallowed use of Unset variable ':form'; you may want '$no-value'"})

(deferr cannot-index-into-empty-vector [data]
        {:message "Cannot index into empty vector"})

(deferr index-not-integer [data]
        {:message "Index must be an integer when target is a vector"})

(deferr index-not-variable-name [data]
        {:message "Index must be a variable name (as a keyword) when target is an instance"})

(deferr no-such-variable [data]
        {:message "No such variable ':index-form' on spec ':spec-id'"})

(deferr invalid-lookup-target [data]
        {:message "Lookup target must be an instance of known type or non-empty vector"})

(deferr result-always [data]
        {:message "Result of ':op' would always be :value"})

(deferr if-expects-boolean [data]
        {:message "First argument to 'if' must be boolean"})

(deferr when-expects-boolean [data]
        {:message "First argument to 'when' must be boolean"})

(deferr let-needs-bare-symbol [data]
        {:message "Binding target for 'let' must be a bare symbol, not: :sym"})

(deferr let-invalid-symbol [data]
        {:message "Binding target for 'let' must not start with '$': :sym"})

(deferr cannot-bind-unset [data]
        {:message "Disallowed binding ':sym' to :Unset value; just use '$no-value'"})

(deferr cannot-bind-nothing [data]
        {:message "Disallowed binding ':sym' to :Nothing value; perhaps move to body of 'let'"})

(deferr invalid-binding-form [data]
        {:message "Binding form for ':op' must have one variable and one collection"})

(deferr invalid-binding-target [data]
        {:message "Binding target for ':op' must be a bare symbol, not: :sym"})

(deferr binding-target-invalid-symbol [data]
        {:message "Binding target for ':op' must not start with '$': :sym"})

(deferr collection-required [data]
        {:message "Collection required for ':op', not :expr-type-string"})

(deferr body-must-be-boolean [data]
        {:message "Body expression in ':op' must be boolean"})

(deferr body-must-be-integer [data]
        {:message "Body expression in 'sort-by' must be Integer, not :body-type"})

(deferr invalid-accumulator [data]
        {:message "Accumulator binding target for ':op' must be a bare symbol, not: :accumulator"})

(deferr invalid-element-binding-target [data]
        {:message "Element binding target for ':op' must be a bare symbol, not: :element"})

(deferr cannot-use-same-symbol [data]
        {:message "Cannot use the same symbol for accumulator and element binding: :element"})

(deferr reduce-needs-vector [data]
        {:message "Second binding expression to 'reduce' must be a vector."})

(deferr first-argument-not-bare-symbol [data]
        {:message "First argument to ':op' must be a bare symbol"})

(deferr first-agument-not-optional [data]
        {:message "First argument to ':op' must have an optional type"})

(deferr binding-expression-not-optional [data]
        {:message "Binding expression in ':op' must have an optional type"})

(deferr first-needs-vector [data]
        {:message "Argument to 'first' must be a vector"})

(deferr argument-empty [data]
        {:message "Argument to first is always empty"})

(deferr rest-needs-vector [data]
        {:message "Argument to 'rest' must be a vector"})

(deferr needs-collection [data]
        {:message "First argument to ':op' must be a set or vector"})

(deferr needs-collection-second [data]
        {:message "Second argument to ':op' must be a set or vector"})

(deferr cannot-conj-unset [data]
        {:message "Cannot conj possibly unset value to :type-string"})

(deferr argument-mis-match [data]
        {:message "When first argument to ':op' is a vector, second argument must also be a vector"})

(deferr must-be-instance [data]
        {:message "First argument to ':op' must be an instance"})

(deferr must-be-spec-id [data]
        {:message "Second argument to ':op' must be a spec id"})

(deferr spec-not-found [data]
        {:message "Spec not found: ':spec-id'"})

(deferr unknown-type [data]
        {:message "Argument to ':op' must be an instance of known type"})

(deferr syntax-error [data]
        {:message "Syntax error"})

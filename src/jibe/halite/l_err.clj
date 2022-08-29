;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.l-err
  (:require [jibe.lib.format-errors :refer [deferr]]))

(set! *warn-on-reflection* true)

(deferr disallowed-nothing [data]
        {:message "Disallowed '\\colonNothing' expression: :nothing-arg"})

(deferr no-matching-signature [data]
        {:message "No matching signature for ':op'"})

(deferr undefined [data]
        {:message "Undefined: ':form'"})

(deferr undefined-use-of-unset-variable [data]
        {:message "Disallowed use of Unset variable ':form'; you may want '$no-value'"})

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

(deferr let-needs-symbol [data]
        {:message "Binding target for 'let' must be a symbol, not: :sym"})

(deferr cannot-bind-unset [data]
        {:message "Disallowed binding ':sym' to \\colonUnset value; just use '$no-value'"})

(deferr cannot-bind-nothing [data]
        {:message "Disallowed binding ':sym' to \\colonNothing value; perhaps move to body of 'let'"})

(deferr invalid-binding-form [data]
        {:message "Binding form for ':op' must have one variable and one collection"})

(deferr invalid-binding-target [data]
        {:message "Binding target for ':op' must be a symbol, not: :sym"})

(deferr binding-target-invalid-symbol [data]
        {:message "Binding target for ':op' must not start with '$': :sym"})

(deferr collection-required [data]
        {:message "Collection required for ':op', not :expr-type-string"})

(deferr invalid-accumulator [data]
        {:message "Accumulator binding target for ':op' must be a symbol, not: :accumulator"})

(deferr invalid-element-binding-target [data]
        {:message "Element binding target for ':op' must be a symbol, not: :element"})

(deferr reduce-needs-vector [data]
        {:message "Second binding expression to 'reduce' must be a vector."})

(deferr first-argument-not-optional [data]
        {:message "First argument to ':op' must have an optional type"})

(deferr binding-expression-not-optional [data]
        {:message "Binding expression in ':op' must have an optional type"})

(deferr needs-collection [data]
        {:message "First argument to ':op' must be a set or vector"})

(deferr needs-collection-second [data]
        {:message "Second argument to ':op' must be a set or vector"})

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

(deferr let-bindings-empty [data]
        {:message "Bindings form of 'let' cannot be empty in: :form"})

(deferr get-in-path-cannot-be-empty [data]
        {:message "The path parameter in 'get-in' cannot be empty: :form"})

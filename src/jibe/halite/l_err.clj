;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.l-err
  (:require [jibe.lib.format-errors :refer [deferr]]))

(set! *warn-on-reflection* true)

(deferr disallowed-nothing [data]
        {:message "Disallowed '\\colonNothing' expression: :nothing-arg"})

(deferr no-matching-signature [data]
        {:message "No matching signature for ':op'"})

(deferr disallowed-unset-variable [data]
        {:message "Disallowed use of Unset variable ':form'; you may want '$no-value'"})

(deferr no-such-variable [data]
        {:message "No such variable ':index-form' on spec ':spec-id'"})

(deferr result-always-known [data]
        {:message "Result of ':op' would always be :value"})

(deferr if-expects-boolean [data]
        {:message "First argument to 'if' must be boolean"})

(deferr let-needs-symbol [data]
        {:message "Binding target for 'let' must be a symbol, not: :sym"})

(deferr cannot-bind-unset [data]
        {:message "Disallowed binding ':sym' to \\colonUnset value; just use '$no-value'"})

(deferr cannot-bind-nothing [data]
        {:message "Disallowed binding ':sym' to \\colonNothing value; perhaps move to body of 'let'"})

(deferr binding-target-invalid-symbol [data]
        {:message "Binding target for ':op' must not start with '$': :sym"})

(deferr collection-required [data]
        {:message "Collection required for ':op', not :expr-type-string"})

(deferr first-argument-not-optional [data]
        {:message "First argument to ':op' must have an optional type"})

(deferr binding-expression-not-optional [data]
        {:message "Binding expression in ':op' must have an optional type"})

(deferr syntax-error [data]
        {:message "Syntax error"})

(deferr let-bindings-empty [data]
        {:message "Bindings form of 'let' cannot be empty in: :form"})

(deferr get-in-path-cannot-be-empty [data]
        {:message "The path parameter in 'get-in' cannot be empty: :form"})

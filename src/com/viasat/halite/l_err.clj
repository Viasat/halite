;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.l-err
  (:require [com.viasat.halite.lib.format-errors :refer [deferr]]))

(set! *warn-on-reflection* true)

(deferr disallowed-nothing [data]
        {:template "Disallowed '\\colonNothing' expression: :nothing-arg"})

(deferr disallowed-unset-variable [data]
        {:template "Disallowed use of Unset variable ':form'; you may want '$no-value'"})

(deferr result-always-known [data]
        {:template "Result of ':op' would always be :value"})

(deferr cannot-bind-unset [data]
        {:template "Disallowed binding ':sym' to \\colonUnset value; just use '$no-value'"})

(deferr cannot-bind-nothing [data]
        {:template "Disallowed binding ':sym' to \\colonNothing value; perhaps move to body of 'let'"})

(deferr binding-target-invalid-symbol [data]
        {:template "Binding target for ':op' must not start with '$': :sym"})

(deferr first-argument-not-optional [data]
        {:template "First argument to ':op' must have an optional type"})

(deferr binding-expression-not-optional [data]
        {:template "Binding expression in ':op' must have an optional type"})

(deferr let-bindings-empty [data]
        {:template "Bindings form of 'let' cannot be empty in: :form"})

(deferr get-in-path-empty [data]
        {:template "The path parameter in 'get-in' cannot be empty: :form"})

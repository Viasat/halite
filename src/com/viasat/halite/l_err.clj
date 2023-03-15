;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.l-err
  (:require [com.viasat.halite.lib.format-errors :as format-errors]))

(set! *warn-on-reflection* true)

(format-errors/deferr disallowed-nothing [data]
                      {:template "Disallowed '\\colonNothing' expression: :nothing-arg"})

(format-errors/deferr disallowed-unset-variable [data]
                      {:template "Disallowed use of Unset variable ':form'; you may want '$no-value'"})

(format-errors/deferr result-always-known [data]
                      {:template "Result of ':op' would always be :value"})

(format-errors/deferr cannot-bind-unset [data]
                      {:template "Disallowed binding ':sym' to \\colonUnset value; just use '$no-value'"})

(format-errors/deferr cannot-bind-nothing [data]
                      {:template "Disallowed binding ':sym' to \\colonNothing value; perhaps move to body of 'let'"})

(format-errors/deferr binding-target-invalid-symbol [data]
                      {:template "Binding target for ':op' must not start with '$': :sym"})

(format-errors/deferr first-argument-not-optional [data]
                      {:template "First argument to ':op' must have an optional type"})

(format-errors/deferr binding-expression-not-optional [data]
                      {:template "Binding expression in ':op' must have an optional type"})

(format-errors/deferr let-bindings-empty [data]
                      {:template "Bindings form of 'let' cannot be empty in: :form"})

(format-errors/deferr get-in-path-empty [data]
                      {:template "The path parameter in 'get-in' cannot be empty: :form"})

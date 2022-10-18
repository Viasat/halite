;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.data-spec-bnf)

(def type-bnf-vector
  ['fixed-decimal-type {:bnf "\"Decimal1\" | \"Decimal2\" | \"Decimal3\" | \"Decimal4\" | \"Decimal5\" | \"Decimal6\" | \"Decimal7\" | \"Decimal8\" | \"Decimal9\" | \"Decimal10\" | \"Decimal11\" | \"Decimal12\" | \"Decimal13\" | \"Decimal14\" | \"Decimal15\" | \"Decimal16\" | \"Decimal17\" | \"Decimal18\" "}
   'primitive-type {:bnf "\"Integer\" | \"String\" | \"Boolean\" | fixed-decimal-type "}
   'collection-type {:bnf "primitive-type | '[' primitive-type ']' | '#{' primitive-type '}'"}
   'maybe-type {:bnf "collection-type | '[' ':Maybe' collection-type ']'"}])

(def spec-var-map-bnf-pair
  ['spec-var-map {:bnf "'{' {bare-keyword maybe-type} '}'"}])

(def constraints-bnf-pair
  ['constraints {:bnf "'[' { '[' string:name  halite-expr ']' } ']'"}])

(def refinement-map-bnf-pair
  ['refinement-map {:bnf " '{' {namespaced-keyword:spec-id '{' ':name' string ':expr' halite-expr [':inverted?' boolean] '}' } '}'"}])

(def spec-map-bnf-pair
  ['spec-map {:bnf "'{' {namespaced-keyword:spec-id '{' { (':abstract?' boolean) | (':spec-vars' spec-var-map) |  (':constraints' constraints) | (':refines-to' refinement-map) } '}'} '}'"}])


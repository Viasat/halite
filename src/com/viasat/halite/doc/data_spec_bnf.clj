;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.data-spec-bnf)

(set! *warn-on-reflection* true)

(def type-bnf-vector
  ['fixed-decimal-scale {:bnf "0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18"}
   'fixed-decimal-type {:bnf "'[' ':Decimal' fixed-decimal-scale ']'"}
   'primitive-type {:bnf "':Integer' | ':String' | ':Boolean' | fixed-decimal-type "}
   'collection-type {:bnf "primitive-type | '[' { ':Vec' | ':Set' } primitive-type ']'"}
   'maybe-type {:bnf "collection-type | '[' ':Maybe' collection-type ']'"}])

(def spec-var-map-bnf-pair
  ['spec-var-map {:bnf "'{' {bare-keyword maybe-type} '}'"}])

(def constraint-set-bnf
  ['constraint-set {:bnf " '#{' { '{' ':name' string ':expr' halite-expr '}' } '}'"}])

(def refinement-map-bnf-pair
  ['refinement-map {:bnf " '{' {namespaced-keyword:spec-id '{' ':name' string ':expr' halite-expr [':inverted?' boolean] '}' } '}'"}])

(def spec-map-bnf-pair
  ['spec-map {:bnf "'{' {namespaced-keyword:spec-id '{' { (':abstract?' boolean) | (':spec-vars' spec-var-map) |  (':constraints' constraint-set) | (':refines-to' refinement-map) } '}'} '}'"}])


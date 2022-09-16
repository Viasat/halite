<!---
  This markdown file was generated. Do not edit.
  -->

## Use an instance as a function

Consider there is some logic that needs to be reused in multiple contexts. How to package it up so that it can be reused?

It is a bit convoluted, but consider the following specs.

```clojure
{:spec/Add {:spec-vars {:x "Integer", :y "Integer"},
            :refines-to {:spec/IntegerResult {:name "refine_to_result",
                                              :expr {:$type :spec/IntegerResult,
                                                     :result (+ x y)}}}},
 :spec/IntegerResult {:spec-vars {:result "Integer"}}}
```

This makes a spec which when instantiated is allows a refinement expression to be invoked as a sort of function call.

```clojure
(let [x 2 y 3 result (get (refine-to {:$type :spec/Add, :x x, :y y} :spec/IntegerResult) :result)] result)


;-- result --
5
```

This is not necessarily recommended, but it is possible.

#### Basic elements:

[`instance`](../halite-basic-syntax-reference.md#instance)

#### Operator reference:

* [`refine-to`](../halite-full-reference.md#refine-to)



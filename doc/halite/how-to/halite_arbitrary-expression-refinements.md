<!---
  This markdown file was generated. Do not edit.
  -->

## Arbitrary expression in refinements

How to write arbitrary expressions to convert instances.

Refinement expressions can be arbitrary expressions over the fields of the instance or constant values.

```clojure
{:spec/A$v4 {:spec-vars {:b "Integer",
                         :c "Integer",
                         :d "String"},
             :refines-to {:spec/X$v4 {:name "refine_to_X",
                                      :expr '{:$type :spec/X$v4,
                                              :x (+ b c),
                                              :y 12,
                                              :z (if (= "medium" d) 5 10)}}}},
 :spec/X$v4 {:spec-vars {:x "Integer",
                         :y "Integer",
                         :z "Integer"}}}
```

```clojure
(let [a {:$type :spec/A$v4,
         :b 1,
         :c 2,
         :d "large"}]
  (refine-to a :spec/X$v4))


;-- result --
{:$type :spec/X$v4,
 :x 3,
 :y 12,
 :z 10}
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### Operator reference:

* [`refine-to`](../halite_full-reference.md#refine-to)


#### How Tos:

* [convert-instances](../how-to/halite_convert-instances.md)


#### Explanations:

* [refinement-implications](../explanation/halite_refinement-implications.md)



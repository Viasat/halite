<!---
  This markdown file was generated. Do not edit.
  -->

## Refinements as general purpose functions

Refinements can be used as general purpose instance conversion functions.

A refinement can be defined that does not convert from a concrete instance to a more abstract instance, but in fact converts in the opposite direction.

```clojure
{:spec/Car {:refines-to {:spec/Ford {:name "refine_to_ford",
                                     :expr '{:$type :spec/Ford,
                                             :model "Mustang",
                                             :year 2000}}}},
 :spec/Ford {:spec-vars {:model "String",
                         :year "Integer"}}}
```

In this example a highly abstract instance, just called a car, is converted into a concrete instance that has more detailed information.

```clojure
(refine-to {:$type :spec/Car} :spec/Ford)


;-- result --
{:$type :spec/Ford,
 :model "Mustang",
 :year 2000}
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference.md#instance)

#### Operator reference:

* [`refine-to`](halite_full-reference.md#refine-to)


#### How Tos:

* [convert-instances](../how-to/halite_convert-instances.md)


#### Tutorials:

* [grocery](../how-to/halite_grocery.md)


#### Explanations:

* [abstract-spec](../how-to/halite_abstract-spec.md)
* [refinement-implications](../how-to/halite_refinement-implications.md)
* [refinement-terminology](../how-to/halite_refinement-terminology.md)



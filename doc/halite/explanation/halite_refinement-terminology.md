<!---
  This markdown file was generated. Do not edit.
  -->

## Clarify terminology around refinements

The primary intent of a refinement is to be a mechanism to translate instances of more concrete specifications into more abstract specifications.

The refinement has a direction in terms of converting X to Y.

```clojure
{:spec/X$v1 {:refines-to {:spec/Y$v1 {:name "refine_to_Y",
                                      :expr '{:$type :spec/Y$v1}}}},
 :spec/Y$v1 {}}
```

```clojure
(refine-to {:$type :spec/X$v1} :spec/Y$v1)


;-- result --
{:$type :spec/Y$v1}
```

This direction of the spec is the same, regardless of whether the refinement is intrinsic or extrinsic.

```clojure
{:spec/X$v2 {:refines-to {:spec/Y$v2 {:name "refine_to_Y",
                                      :expr '{:$type :spec/Y$v2},
                                      :extrinsic? true}}},
 :spec/Y$v2 {}}
```

The extrinsic flag determines whether the constraints of Y are applied to all instances of X, but it does not affect the basic 'direction' of the refinement. i.e. the refinement still converts instances of X into instances of Y.

```clojure
(refine-to {:$type :spec/X$v2} :spec/Y$v2)


;-- result --
{:$type :spec/Y$v2}
```

The use of the word 'to' in 'refine-to' and 'refines-to?' refers to this direction of the refinement mapping. So 'refine-to' means to 'execute a refinement', specifically a refinement that converts instances 'to' instances of the indicated spec.

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### Operator reference:

* [`refine-to`](../halite_full-reference.md#refine-to)


#### How Tos:

* [convert-instances](../how-to/halite_convert-instances.md)


#### Explanations:

* [abstract-spec](../explanation/halite_abstract-spec.md)
* [refinement-implications](../explanation/halite_refinement-implications.md)
* [refinements-as-functions](../explanation/halite_refinements-as-functions.md)



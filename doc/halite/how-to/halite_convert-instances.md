<!---
  This markdown file was generated. Do not edit.
  -->

## Converting instances between specs

How to convert an instance from one spec type to another.

An expression can convert an instance of one type to the instance of another type. Assume there are these two specs.

```clojure
{:spec/A$v1 {:fields {:b :Integer}},
 :spec/X$v1 {:fields {:y :Integer}}}
```

The following expression converts an instance of the first spec into an instance of the second.

```clojure
(let [a {:$type :spec/A$v1,
         :b 1}]
  {:$type :spec/X$v1,
   :y (get a :b)})


;-- result --
{:$type :spec/X$v1,
 :y 1}
```

This work, but the language has a built-in idea of 'refinements' that allow such conversion functions to be expressed in a way that the system understands.

```clojure
{:spec/A$v2 {:fields {:b :Integer},
             :refines-to {:spec/X$v2 {:name "refine_to_X",
                                      :expr '{:$type :spec/X$v2,
                                              :y b}}}},
 :spec/X$v2 {:fields {:y :Integer}}}
```

The refinement can be invoked as follows:

```clojure
(let [a {:$type :spec/A$v2,
         :b 1}]
  (refine-to a :spec/X$v2))


;-- result --
{:$type :spec/X$v2,
 :y 1}
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### Operator reference:

* [`refine-to`](../halite_full-reference.md#refine-to)


#### How Tos:

* [arbitrary-expression-refinements](../how-to/halite_arbitrary-expression-refinements.md)
* [convert-instances-transitively](../how-to/halite_convert-instances-transitively.md)
* [optionally-convert-instances](../how-to/halite_optionally-convert-instances.md)


#### Tutorials:

* [grocery](../tutorial/halite_grocery.md)



<!---
  This markdown file was generated. Do not edit.
  -->

## What constraints are implied by refinement?

Specs can be defined as refining other specs. When this is done what constraints are implied by the refinement?

One spec can be defined to be a refinement of another spec. First consider a square which has a width and height. The constraint, which makes it a square, requires these two values to be equal.

```clojure
{:spec/Square {:spec-vars {:height "Integer",
                           :width "Integer"},
               :constraints [["square" '(= width height)]]}}
```

So the following is a valid spec/Square

```clojure
{:$type :spec/Square,
 :height 5,
 :width 5}
```

While the following is not a valid spec/Square

```clojure
{:$type :spec/Square,
 :height 6,
 :width 5}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Square', violates constraints square"
 :h-err/invalid-instance]
```

Now consider a new spec, 'spec/Box', and we define that it refines to 'spec/Square'.

```clojure
{:spec/Box$v1 {:spec-vars {:length "Integer",
                           :width "Integer"},
               :refines-to {:spec/Square {:name "refine_to_square",
                                          :expr '{:$type :spec/Square,
                                                  :width width,
                                                  :height length}}}},
 :spec/Square {:spec-vars {:height "Integer",
                           :width "Integer"},
               :constraints [["square" '(= width height)]]}}
```

The refinement allows a 'spec/Square' instance to be computed from a 'spec/Box'

```clojure
(refine-to {:$type :spec/Box$v1,
            :width 5,
            :length 5}
           :spec/Square)


;-- result --
{:$type :spec/Square,
 :height 5,
 :width 5}
```

But furthermore, notice that the refinement has by implication created a constraint on 'spec/Box' itself.

```clojure
{:$type :spec/Box$v1,
 :length 6,
 :width 5}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Square', violates constraints square"
 :h-err/invalid-instance]
```

It is not possible to construct an instance of 'spec/Box' which is not a 'spec/Square' because we have said that all instances of 'spec/Box' can be translated into a 'spec/Square', and more speficically into a valid instance of 'spec/Square, which is the only kind of instance that the system recognizes.

If this was not the intent, and rather the intent was to indicate that some instances of 'spec/Box' can be converted into 'spec/Square' instances then the refinement would be defined as:

```clojure
{:spec/Box$v2 {:spec-vars {:length "Integer",
                           :width "Integer"},
               :refines-to {:spec/Square {:name "refine_to_square",
                                          :expr '(when (= width length)
                                                   {:$type :spec/Square,
                                                    :width width,
                                                    :height length})}}},
 :spec/Square {:spec-vars {:height "Integer",
                           :width "Integer"},
               :constraints [["square" '(= width height)]]}}
```

Now it is possible to construct a box that is not a square.

```clojure
{:$type :spec/Box$v2,
 :length 6,
 :width 5}


;-- result --
{:$type :spec/Box$v2,
 :length 6,
 :width 5}
```

But, if we attempt to refine such a box to a square, an error is produced:

```clojure
(refine-to {:$type :spec/Box$v2,
            :width 5,
            :length 6}
           :spec/Square)


;-- result --
[:throws
 "h-err/no-refinement-path 0-0 : No active refinement path from 'spec/Box$v2' to 'spec/Square'"
 :h-err/no-refinement-path]
```

Alternatively, we can simply ask whether the box can be converted to a square:

```clojure
(refines-to? {:$type :spec/Box$v2,
              :width 5,
              :length 6}
             :spec/Square)


;-- result --
false
```

Another way of defining the refinement is to declare it to be 'inverted?'. What this means is that the refinement will be applied where possible, and where it results in a contradiction then a runtime error is produced.

```clojure
{:spec/Box$v3 {:spec-vars {:length "Integer",
                           :width "Integer"},
               :refines-to {:spec/Square {:name "refine_to_square",
                                          :expr '{:$type :spec/Square,
                                                  :width width,
                                                  :height length},
                                          :inverted? true}}},
 :spec/Square {:spec-vars {:height "Integer",
                           :width "Integer"},
               :constraints [["square" '(= width height)]]}}
```

Note that in this version of the refinement the guard clause in the refinement expression has been removed, which means the refinement applies to all instances of box. However, the refinement has been declared to be 'inverted?'. This means that even if the resulting square instance would violate the constraints of spec/Square, the spec/Box instance is still valid.

```clojure
{:$type :spec/Box$v3,
 :length 6,
 :width 5}


;-- result --
{:$type :spec/Box$v3,
 :length 6,
 :width 5}
```

The box itself is valid, but now attempting to refine a non-square box into a square will produce a runtime error.

```clojure
(refine-to {:$type :spec/Box$v3,
            :width 5,
            :length 6}
           :spec/Square)


;-- result --
[:throws
 "h-err/refinement-error 0-0 : Refinement from 'spec/Box$v3' failed unexpectedly: \"h-err/invalid-instance 0-0 : Invalid instance of 'spec/Square', violates constraints square\""
 :h-err/refinement-error]
```

Of course for a square box the refinement works as expected.

```clojure
(refine-to {:$type :spec/Box$v3,
            :width 5,
            :length 5}
           :spec/Square)


;-- result --
{:$type :spec/Square,
 :height 5,
 :width 5}
```

A way of summarizing the two approaches to a refinement are: for 'normal' refinements, the refinement implies that the author of the spec is intending to incorporate all of the constraints implied by the refinement into the spec at hand. However, for an inverted refinement, the spec at hand is being defined independent of constraints implied by the refinement. Instead, it is the responsibility of the refinement expression to deal with all of the implications of the constraints of the spec being refined to. If the refinement expression does not take all the implications into account, then a runtime error results.

As an advanced topic, there is a 'valid?' operator which deals with immediate constraint violations in the spec at hand, but it does not handle the case of the application of an inverted refinement leading to a constraint violation.

```clojure
(valid? (refine-to {:$type :spec/Box$v3,
                    :width 5,
                    :length 6}
                   :spec/Square))


;-- result --
[:throws
 "h-err/refinement-error 0-0 : Refinement from 'spec/Box$v3' failed unexpectedly: \"h-err/invalid-instance 0-0 : Invalid instance of 'spec/Square', violates constraints square\""
 :h-err/refinement-error]
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### Operator reference:

* [`refine-to`](../halite_full-reference.md#refine-to)
* [`refines-to?`](../halite_full-reference.md#refines-to_Q)
* [`valid?`](../halite_full-reference.md#valid_Q)


#### How Tos:

* [arbitrary-expression-refinements](../how-to/halite_arbitrary-expression-refinements.md)


#### Explanations:

* [abstract-spec](../explanation/halite_abstract-spec.md)
* [refinement-terminology](../explanation/halite_refinement-terminology.md)



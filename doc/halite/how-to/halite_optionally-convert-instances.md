<!---
  This markdown file was generated. Do not edit.
  -->

## Optionally converting instances between specs

Consider there are some cases where an instance can be converted to another spec, but other cases where it cannot be. Refinement expressions can include logic to optionally convert an instance.

In the following example, the refinement expression determines whether to convert an instance based on the value of 'b'.

```clojure
{:spec/A$v1 {:fields {:b :Integer},
             :refines-to {:spec/X$v1 {:name "refine_to_X",
                                      :expr '(when (> b 10)
                                               {:$type :spec/X$v1,
                                                :y b})}}},
 :spec/X$v1 {:fields {:y :Integer}}}
```

In this example, the refinement applies.

```clojure
(refine-to {:$type :spec/A$v1,
            :b 20}
           :spec/X$v1)


;-- result --
{:$type :spec/X$v1,
 :y 20}
```

In this example, the refinement does not apply

```clojure
(refine-to {:$type :spec/A$v1,
            :b 5}
           :spec/X$v1)


;-- result --
[:throws
 "h-err/no-refinement-path 0-0 : No active refinement path from 'spec/A$v1' to 'spec/X$v1'"
 :h-err/no-refinement-path]
```

A refinement path can be probed to determine if it exists and if it applies to a given instance.

```clojure
(refines-to? {:$type :spec/A$v1,
              :b 20}
             :spec/X$v1)


;-- result --
true
```

```clojure
(refines-to? {:$type :spec/A$v1,
              :b 5}
             :spec/X$v1)


;-- result --
false
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### Operator reference:

* [`refine-to`](../halite_full-reference.md#refine-to)
* [`refines-to?`](../halite_full-reference.md#refines-to_Q)


#### How Tos:

* [convert-instances](../how-to/halite_convert-instances.md)



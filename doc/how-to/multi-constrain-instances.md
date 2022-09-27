<!---
  This markdown file was generated. Do not edit.
  -->

## Defining multiple constraints on instance values

How to define multiple constraints in a spec

Multiple constraints can be defined on a spec. Each constraint must have a unique name within the context of a spec.

```clojure
{:spec/A$v1 {:spec-vars {:b "Integer",
                         :c "Integer"},
             :constraints [["constrain_b" '(> b 100)]
                           ["constrain_c" '(< c 20)]]}}
```

An instance must satisfy all of the constraints to be valid

```clojure
{:$type :spec/A$v1,
 :b 101,
 :c 19}


;-- result --
{:$type :spec/A$v1,
 :b 101,
 :c 19}
```

Violating any of the constraints makes the instance invalid

```clojure
{:$type :spec/A$v1,
 :b 100,
 :c 19}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v1', violates constraints constrain_b"
 :h-err/invalid-instance]
```

```clojure
{:$type :spec/A$v1,
 :b 101,
 :c 20}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v1', violates constraints constrain_c"
 :h-err/invalid-instance]
```

Mutliple constraints can refer to the same variables.

```clojure
{:spec/A$v2 {:spec-vars {:b "Integer"},
             :constraints [["constrain_b" '(> b 100)]
                           ["constrain_b2" '(< b 110)]]}}
```

```clojure
{:$type :spec/A$v2,
 :b 105}


;-- result --
{:$type :spec/A$v2,
 :b 105}
```

```clojure
{:$type :spec/A$v2,
 :b 120}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v2', violates constraints constrain_b2"
 :h-err/invalid-instance]
```

In general, constraint extpressions can be combined with a logical 'and'. This has the same meaning because all constraints are effectively 'anded' together to produce a single logical predicate to assess whether an instance is valid. So, decomposing constraints into separate constraints is largely a matter of organizing and naming the checks to suit the modelling exercise.

```clojure
{:spec/A$v3 {:spec-vars {:b "Integer"},
             :constraints [["constrain_b" '(and (> b 100) (< b 110))]]}}
```

```clojure
{:$type :spec/A$v3,
 :b 105}


;-- result --
{:$type :spec/A$v3,
 :b 105}
```

```clojure
{:$type :spec/A$v3,
 :b 120}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v3', violates constraints constrain_b"
 :h-err/invalid-instance]
```

### Reference

#### Basic elements:

[`instance`](../halite-basic-syntax-reference.md#instance)

#### How tos:

* [constrain-instances](../how-to/constrain-instances.md)



<!---
  This markdown file was generated. Do not edit.
  -->

## Defining constraints on instance values

How to constrain the possible values for instance fields

As a starting point specs specify the fields that make up instances.

```clojure
{:spec/A$v1 {:spec-vars {:b "Integer"}}}
```

This indicates that 'b' must be an integer, but it doesn't indicate what valid values are. The following spec includes a constraint that requires b to be greater than 100.

```clojure
{:spec/A$v2 {:spec-vars {:b "Integer"},
             :constraints [["constrain_b" (> b 100)]]}}
```

An attempt to make an instance that satisfies this constraint is successful

```clojure
{:$type :spec/A$v2,
 :b 200}


;-- result --
{:$type :spec/A$v2,
 :b 200}
```

However, an attempt to make an instance that violates this constraint fails.

```clojure
{:$type :spec/A$v2,
 :b 50}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v2', violates constraints constrain_b"
 :h-err/invalid-instance]
```

Constraints can be arbitrary expressions that refer to multiple fields.

```clojure
{:spec/A$v3 {:spec-vars {:b "Integer",
                         :c "Integer"},
             :constraints [["constrain_b_c" (< (+ b c) 10)]]}}
```

In this example, the sum of 'a' and 'b' must be less than 10

```clojure
{:$type :spec/A$v3,
 :b 2,
 :c 3}


;-- result --
{:$type :spec/A$v3,
 :b 2,
 :c 3}
```

```clojure
{:$type :spec/A$v3,
 :b 6,
 :c 7}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v3', violates constraints constrain_b_c"
 :h-err/invalid-instance]
```

#### Basic elements:

[`instance`](../halite-basic-syntax-reference.md#instance)

#### See also:

* [multi-constrain-instances](multi-constrain-instances.md)



<!---
  This markdown file was generated. Do not edit.
  -->

## String as enumeration

How to model an enumeration as a string

Say we want to model a shirt size and the valid values are "small", "medium", and "large". We can start by modeling the size as a string.

```clojure
{:spec/Shirt$v1 {:spec-vars {:size "String"}}}
```

This is a start, but it allows invalid size values.

```clojure
{:$type :spec/Shirt$v1,
 :size "XL"}


;-- result --
{:$type :spec/Shirt$v1,
 :size "XL"}
```

So we can add a constraint to limit the values to what we expect.

```clojure
{:spec/Shirt$v2 {:spec-vars {:size "String"},
                 :constraints {:size_constraint '(contains? #{"small" "medium"
                                                              "large"}
                                                            size)}}}
```

Now the shirt with the invalid size cannot be constructed.

```clojure
{:$type :spec/Shirt$v2,
 :size "XL"}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Shirt$v2', violates constraints size_constraint"
 :h-err/invalid-instance]
```

But a shirt with a valid size can be constructed.

```clojure
{:$type :spec/Shirt$v2,
 :size "medium"}


;-- result --
{:$type :spec/Shirt$v2,
 :size "medium"}
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### How Tos:

* [spec-variables](../how-to/halite_spec-variables.md)



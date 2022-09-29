<!---
  This markdown file was generated. Do not edit.
  -->

## Transform a vector into a single value

Consider that you have a vector of values and you need to produce a single value that takes into account all of the values in the vector.

A vector of values can be transformed into a single value. For example, the following transforms a vector of integers into a single value, which is their product.

```clojure
(let [v [1 2 3]]
  (reduce [a 1] [x v] (* a x)))


;-- result --
6
```

The values from the vector are combined with the accumulator, one-by-one in order and then the final value of the accumulator is produced.

### Reference

#### Basic elements:

[`vector`](../halite_basic-syntax-reference.md#vector)

#### Operator reference:

* [`*`](../halite_full-reference.md#_S)
* [`reduce`](../halite_full-reference.md#reduce)


#### How Tos:

* [transform](../how-to/halite_transform.md)



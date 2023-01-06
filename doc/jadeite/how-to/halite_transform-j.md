<!---
  This markdown file was generated. Do not edit.
  -->

## Transform a collection

Consider that you have a collection of values and need to produce a collection of new values derived from the first.

A collection of values can be transformed into a new collection of values. For example, the following transforms a vector of integers into a new vector of integers.

```java
({ v = [1, 2, 3]; map(x in v)(x * 10) })


//-- result --
[10, 20, 30]
```

The same works with sets.

```java
({ s = #{1, 2, 3}; map(x in s)(x * 10) })


//-- result --
#{10, 20, 30}
```

### Reference

#### Basic elements:

[`set`](../halite_basic-syntax-reference-j.md#set), [`vector`](../halite_basic-syntax-reference-j.md#vector)

#### Operator reference:

* [`*`](../halite_full-reference-j.md#_S)
* [`map`](../halite_full-reference-j.md#map)


#### How Tos:

* [reduce](../how-to/halite_reduce-j.md)



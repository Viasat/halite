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

#### Basic elements:

[`set`](../jadeite-basic-syntax-reference.md#set), [`vector`](../jadeite-basic-syntax-reference.md#vector)

#### Operator reference:

* [`*`](../jadeite-full-reference.md#_S)
* [`map`](../jadeite-full-reference.md#map)


#### See also:

* [reduce](reduce.md)



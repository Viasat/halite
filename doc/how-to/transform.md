<!---
  This markdown file was generated. Do not edit.
  -->

## Transform a collection

Consider that you have a collection of values and need to produce a collection of new values derived from the first.

A collection of values can be transformed into a new collection of values. For example, the following transforms a vector of integers into a new vector of integers.

```clojure
(let [v [1 2 3]]
  (map [x v] (* x 10)))


;-- result --
[10 20 30]
```

The same works with sets.

```clojure
(let [s #{1 3 2}]
  (map [x s] (* x 10)))


;-- result --
#{10 20 30}
```

#### Basic elements:

[`set`](../halite-basic-syntax-reference.md#set), [`vector`](../halite-basic-syntax-reference.md#vector)

#### Operator reference:

* [`*`](../halite-full-reference.md#_S)
* [`map`](../halite-full-reference.md#map)


#### How tos:

* [reduce](../how-to/reduce.md)



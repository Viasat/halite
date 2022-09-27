<!---
  This markdown file was generated. Do not edit.
  -->

## Add contents of a set to a vector

A set must be sorted into a vector before it can be appended onto another vector.

This example shows how to combine a set of sortable items into a vector.

```clojure
(let [v [10 20 30]
      s #{50 40}]
  (concat v (sort s)))


;-- result --
[10 20 30 40 50]
```

The same can be done with a set of values that is not intrinsically sortable, but in this case an explicit sort function must be defined.

```clojure
(let [v [[10 20 30]]
      s #{[60] [40 50] [70 80 90]}]
  (concat v (sort-by [e s] (count e))))


;-- result --
[[10 20 30] [60] [40 50] [70 80 90]]
```

Notice that the items in the set were first sorted based on the number of items in the element, then in that sort order the items were appended to the vector, 'v'.

#### Basic elements:

[`set`](../halite-basic-syntax-reference.md#set), [`vector`](../halite-basic-syntax-reference.md#vector)

#### Operator reference:

* [`concat`](../halite-full-reference.md#concat)


#### How tos:

* [combine](combine.md)



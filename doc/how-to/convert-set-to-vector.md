<!---
  This markdown file was generated. Do not edit.
  -->

## Convert a set into a vector

A set can be converted into a vector by sorting it.

Combine a vector into an empty set to effectively convert the vector into a set which contains the same elements.

```clojure
(let [s #{20 30 10}]
  (sort s))


;-- result --
[10 20 30]
```

This only works if the items in the set are intrinsically sortable.

```clojure
(let [s #{[10 20] [30]}]
  (sort s))


;-- result --
[:throws "h-err/no-matching-signature 0-0 : No matching signature for 'sort'"]
```

If the elements of the set are not sortable then use sort-by to convert the set into a vector.

```clojure
(let [s #{[10 20] [30]}]
  (sort-by [e s] (count e)))


;-- result --
[[30] [10 20]]
```

#### Basic elements:

[`set`](../halite-basic-syntax-reference.md#set), [`vector`](../halite-basic-syntax-reference.md#vector)

#### Operator reference:

* [`sort`](../halite-full-reference.md#sort)


#### How tos:

* [convert-vector-to-set](../how-to/convert-vector-to-set.md)
* [remove-duplicates-from-vector](../how-to/remove-duplicates-from-vector.md)



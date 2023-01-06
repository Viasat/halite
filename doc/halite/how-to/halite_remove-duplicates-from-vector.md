<!---
  This markdown file was generated. Do not edit.
  -->

## Remove duplicate values from a vector.

A vector can be converted to a set and back to a vector to remove duplicates.

Starting with a vector that has duplicate values, it can be converted to a set and then back to a vector. In the process, the duplicates will be removed.

```clojure
(let [v [40 10 10 20 30]]
  (sort (concat #{} v)))


;-- result --
[10 20 30 40]
```

Note that this only works if the elements of the vector are sortable. Also note that this causes the items in the vector to be sorted into their natural sort order.

### Reference

#### Basic elements:

[`set`](../halite_basic-syntax-reference.md#set), [`vector`](../halite_basic-syntax-reference.md#vector)

#### Operator reference:

* [`concat`](../halite_full-reference.md#concat)
* [`sort`](../halite_full-reference.md#sort)


#### How Tos:

* [convert-set-to-vector](../how-to/halite_convert-set-to-vector.md)
* [convert-vector-to-set](../how-to/halite_convert-vector-to-set.md)



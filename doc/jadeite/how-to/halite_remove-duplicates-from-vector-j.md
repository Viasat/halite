<!---
  This markdown file was generated. Do not edit.
  -->

## Remove duplicate values from a vector.

A vector can be converted to a set and back to a vector to remove duplicates.

Starting with a vector that has duplicate values, it can be converted to a set and then back to a vector. In the process, the duplicates will be removed.

```java
({ v = [40, 10, 10, 20, 30]; #{}.concat(v).sort() })


//-- result --
[10, 20, 30, 40]
```

Note that this only works if the elements of the vector are sortable. Also note that this causes the items in the vector to be sorted into their natural sort order.

### Reference

#### Basic elements:

[`set`](../halite_basic-syntax-reference-j.md#set), [`vector`](../halite_basic-syntax-reference-j.md#vector)

#### Operator reference:

* [`concat`](halite_full-reference-j.md#concat)
* [`sort`](halite_full-reference-j.md#sort)


#### How Tos:

* [convert-set-to-vector](../how-to/halite_convert-set-to-vector-j.md)
* [convert-vector-to-set](../how-to/halite_convert-vector-to-set-j.md)



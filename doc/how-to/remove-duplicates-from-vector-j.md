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

#### Basic elements:

[`set`](../jadeite-basic-syntax-reference.md#set), [`vector`](../jadeite-basic-syntax-reference.md#vector)

#### Operator reference:

* [`concat`](../jadeite-full-reference.md#concat)
* [`sort`](../jadeite-full-reference.md#sort)


#### How tos:

* [convert-set-to-vector](convert-set-to-vector.md)
* [convert-vector-to-set](convert-vector-to-set.md)



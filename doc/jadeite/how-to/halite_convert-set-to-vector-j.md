<!---
  This markdown file was generated. Do not edit.
  -->

## Convert a set into a vector

A set can be converted into a vector by sorting it.

Combine a vector into an empty set to effectively convert the vector into a set which contains the same elements.

```java
({ s = #{10, 20, 30}; s.sort() })


//-- result --
[10, 20, 30]
```

This only works if the items in the set are intrinsically sortable.

```java
({ s = #{[10, 20], [30]}; s.sort() })


//-- result --
[:throws "h-err/no-matching-signature 0-0 : No matching signature for 'sort'"]
```

If the elements of the set are not sortable then use sort-by to convert the set into a vector.

```java
({ s = #{[10, 20], [30]}; sortBy(e in s)e.count() })


//-- result --
[[30], [10, 20]]
```

### Reference

#### Basic elements:

[`set`](../halite_basic-syntax-reference-j.md#set), [`vector`](../halite_basic-syntax-reference-j.md#vector)

#### Operator reference:

* [`sort`](halite_full-reference-j.md#sort)


#### How Tos:

* [convert-vector-to-set](../how-to/halite_convert-vector-to-set-j.md)
* [remove-duplicates-from-vector](../how-to/halite_remove-duplicates-from-vector-j.md)



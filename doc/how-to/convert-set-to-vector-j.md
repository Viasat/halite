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

#### Basic elements:

[`set`](../jadeite-basic-syntax-reference.md#set), [`vector`](../jadeite-basic-syntax-reference.md#vector)

#### Operator reference:

* [`sort`](../jadeite-full-reference.md#sort)


#### How tos:

* [convert-vector-to-set](convert-vector-to-set.md)
* [remove-duplicates-from-vector](remove-duplicates-from-vector.md)



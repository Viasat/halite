<!---
  This markdown file was generated. Do not edit.
  -->

## Convert a vector into a set

A vector can be converted into a set via 'concat'.

Combine a vector into an empty set to effectively convert the vector into a set which contains the same elements.

```java
({ v = [10, 20, 30]; #{}.concat(v) })


//-- result --
#{10, 20, 30}
```

Note that duplicate elements are removed in the process.

```java
({ v = [10, 10, 20, 30]; #{}.concat(v) })


//-- result --
#{10, 20, 30}
```

#### Basic elements:

[`set`](../jadeite-basic-syntax-reference.md#set), [`vector`](../jadeite-basic-syntax-reference.md#vector)

#### Operator reference:

* [`concat`](../jadeite-full-reference.md#concat)


#### See also:

* [convert-set-to-vector](convert-set-to-vector.md)
* [remove-duplicates-from-vector](remove-duplicates-from-vector.md)



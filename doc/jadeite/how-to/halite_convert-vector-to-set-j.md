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

### Reference

#### Basic elements:

[`set`](../halite_basic-syntax-reference-j.md#set), [`vector`](../halite_basic-syntax-reference-j.md#vector)

#### Operator reference:

* [`concat`](../halite_full-reference-j.md#concat)


#### How Tos:

* [convert-set-to-vector](../how-to/halite_convert-set-to-vector-j.md)
* [remove-duplicates-from-vector](../how-to/halite_remove-duplicates-from-vector-j.md)



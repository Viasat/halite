<!---
  This markdown file was generated. Do not edit.
  -->

## Add contents of a set to a vector

A set must be sorted into a vector before it can be appended onto another vector.

This example shows how to combine a set of sortable items into a vector.

```java
({ v = [10, 20, 30]; s = #{40, 50}; v.concat(s.sort()) })


//-- result --
[10, 20, 30, 40, 50]
```

The same can be done with a set of values that is not intrinsically sortable, but in this case an explicit sort function must be defined.

```java
({ v = [[10, 20, 30]]; s = #{[40, 50], [60], [70, 80, 90]}; v.concat(sortBy(e in s)e.count()) })


//-- result --
[[10, 20, 30], [60], [40, 50], [70, 80, 90]]
```

Notice that the items in the set were first sorted based on the number of items in the element, then in that sort order the items were appended to the vector, 'v'.

### Reference

#### Basic elements:

[`set`](../halite_basic-syntax-reference-j.md#set), [`vector`](../halite_basic-syntax-reference-j.md#vector)

#### Operator reference:

* [`concat`](../halite_full-reference-j.md#concat)


#### How Tos:

* [combine](../how-to/halite_combine-j.md)



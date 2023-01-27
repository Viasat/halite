<!---
  This markdown file was generated. Do not edit.
  -->

## Determine if an item is in a set

How to determine if a given item is contained in a set?

There is a built-in function to determine whether a value is a member of a set.

```java
#{10, 20, 30}.contains?(20)


//-- result --
true
```

The following code correctly determines that a value is not in a set

```java
#{10, 20, 30}.contains?(50)


//-- result --
false
```

It is more verbose, but an alternate solutions is the same as what would be done to determine if an item is in a vector.

```java
({ s = #{10, 20, 30}; t = 20; (any?(x in s)(x == t)) })


//-- result --
true
```

### Reference

#### Basic elements:

[`=`](../halite_basic-syntax-reference-j.md#=), [`any?`](../halite_basic-syntax-reference-j.md#any?), [`set`](../halite_basic-syntax-reference-j.md#set)

#### How Tos:

* [any](../how-to/halite_any-j.md)
* [vector-containment](../how-to/halite_vector-containment-j.md)



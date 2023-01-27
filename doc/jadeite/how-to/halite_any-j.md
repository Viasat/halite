<!---
  This markdown file was generated. Do not edit.
  -->

## Determine if any item in a collection satisfies some criteria

How to determine if any item in a collection satisifies some criteria?

The following code correctly determines that there is at least one value in the vector which makes the test expression true.

```java
({ v = [10, 20, 30]; (any?(x in v)(x > 15)) })


//-- result --
true
```

In this example, no values make the expression true.

```java
({ v = [10, 20, 30]; (any?(x in v)(x > 100)) })


//-- result --
false
```

Sets can be tested in the same way.

```java
({ s = #{10, 20, 30}; (any?(x in s)(x > 15)) })


//-- result --
true
```

### Reference

#### Basic elements:

[`any?`](../halite_basic-syntax-reference-j.md#any?), [`set`](../halite_basic-syntax-reference-j.md#set), [`vector`](../halite_basic-syntax-reference-j.md#vector)

#### How Tos:

* [set-containment](../how-to/halite_set-containment-j.md)
* [vector-containment](../how-to/halite_vector-containment-j.md)



<!---
  This markdown file was generated. Do not edit.
  -->

## Determine if an item is in a vector

Consider that you have a vector and you need to know whether it contains a specific value.

The following code correctly determines that a target value is in a vector.

```java
({ v = [10, 20, 30]; t = 20; any?(x in v)(x == t) })


//-- result --
true
```

The following code correctly determines that a target value is not in a vector.

```java
({ v = [10, 20, 30]; t = 50; any?(x in v)(x == t) })


//-- result --
false
```

#### Basic elements:

[`=`](../jadeite-basic-syntax-reference.md#=), [`vector`](../jadeite-basic-syntax-reference.md#vector)

#### How tos:

* [any](../how-to/any.md)
* [set-containment](../how-to/set-containment.md)



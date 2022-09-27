<!---
  This markdown file was generated. Do not edit.
  -->

## Combine collections together

Consider you have two sets or vectors and need to combine them.

Considering vectors first. One vector can be simply added to the end of another.

```java
({ v1 = [10, 20, 30]; v2 = [40, 50]; v1.concat(v2) })


//-- result --
[10, 20, 30, 40, 50]
```

The same can be done with sets. In this case the sets are simply combined because sets have no instrinsic order to the elements.

```java
({ s1 = #{10, 20, 30}; s2 = #{40, 50}; s1.concat(s2) })


//-- result --
#{10, 20, 30, 40, 50}
```

There is a special set operator that is equivalent to 'concat' for sets.

```java
({ s1 = #{10, 20, 30}; s2 = #{40, 50}; s1.union(s2) })


//-- result --
#{10, 20, 30, 40, 50}
```

However, 'union' only works when all of the arguments are sets. The 'concat' operation can be used to add elements from a vector into a set.

```java
({ s = #{10, 20, 30}; v = [40, 50]; s.concat(v) })


//-- result --
#{10, 20, 30, 40, 50}
```

It is not possible to use concat to add a set into a vector.

```java
({ v = [10, 20, 30]; s = #{40, 50}; v.concat(s) })


//-- result --
[:throws "h-err/not-both-vectors 0-0 : When first argument to 'concat' is a vector, second argument must also be a vector"]
```

This is not supported because a vector is ordered and generally speaking, there is not a deterministic way to add the unordered items from the set into the vector.

### Reference

#### Basic elements:

[`set`](../jadeite-basic-syntax-reference.md#set), [`vector`](../jadeite-basic-syntax-reference.md#vector)

#### How tos:

* [combine-set-to-vector](../how-to/combine-set-to-vector.md)



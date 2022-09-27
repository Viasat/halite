<!---
  This markdown file was generated. Do not edit.
  -->

## Combine collections together

Consider you have two sets or vectors and need to combine them.

Considering vectors first. One vector can be simply added to the end of another.

```clojure
(let [v1 [10 20 30]
      v2 [40 50]]
  (concat v1 v2))


;-- result --
[10 20 30 40 50]
```

The same can be done with sets. In this case the sets are simply combined because sets have no instrinsic order to the elements.

```clojure
(let [s1 #{20 30 10}
      s2 #{50 40}]
  (concat s1 s2))


;-- result --
#{10 20 30 40 50}
```

There is a special set operator that is equivalent to 'concat' for sets.

```clojure
(let [s1 #{20 30 10}
      s2 #{50 40}]
  (union s1 s2))


;-- result --
#{10 20 30 40 50}
```

However, 'union' only works when all of the arguments are sets. The 'concat' operation can be used to add elements from a vector into a set.

```clojure
(let [s #{20 30 10}
      v [40 50]]
  (concat s v))


;-- result --
#{10 20 30 40 50}
```

It is not possible to use concat to add a set into a vector.

```clojure
(let [v [10 20 30]
      s #{50 40}]
  (concat v s))


;-- result --
[:throws
 "h-err/not-both-vectors 0-0 : When first argument to 'concat' is a vector, second argument must also be a vector"]
```

This is not supported because a vector is ordered and generally speaking, there is not a deterministic way to add the unordered items from the set into the vector.

#### Basic elements:

[`set`](../halite-basic-syntax-reference.md#set), [`vector`](../halite-basic-syntax-reference.md#vector)

#### How tos:

* [combine-set-to-vector](../how-to/combine-set-to-vector.md)



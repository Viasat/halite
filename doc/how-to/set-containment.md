<!---
  This markdown file was generated. Do not edit.
  -->

## Determine if an item is in a set

How to determine if a given item is contained in a set?

There is a built-in function to determine whether a value is a member of a set.

```clojure
(contains? #{20 30 10} 20)


;-- result --
true
```

The following code correctly determines that a value is not in a set

```clojure
(contains? #{20 30 10} 50)


;-- result --
false
```

It is more verbose, but an alternate solutions is the same as what would be done to determine if an item is in a vector.

```clojure
(let [s #{20 30 10}
      t 20]
  (any? [x s] (= x t)))


;-- result --
true
```

#### Basic elements:

[`=`](../halite-basic-syntax-reference.md#=), [`any?`](../halite-basic-syntax-reference.md#any?), [`set`](../halite-basic-syntax-reference.md#set)

#### See also:

* [any](any.md)
* [vector-containment](vector-containment.md)



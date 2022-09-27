<!---
  This markdown file was generated. Do not edit.
  -->

## Determine if any item in a collection satisfies some criteria

How to determine if any item in a collection satisifies some criteria?

The following code correctly determines that there is at least one value in the vector which makes the test expression true.

```clojure
(let [v [10 20 30]]
  (any? [x v] (> x 15)))


;-- result --
true
```

In this example, no values make the expression true.

```clojure
(let [v [10 20 30]]
  (any? [x v] (> x 100)))


;-- result --
false
```

Sets can be tested in the same way.

```clojure
(let [s #{20 30 10}]
  (any? [x s] (> x 15)))


;-- result --
true
```

#### Basic elements:

[`any?`](../halite-basic-syntax-reference.md#any?), [`set`](../halite-basic-syntax-reference.md#set), [`vector`](../halite-basic-syntax-reference.md#vector)

#### How tos:

* [set-containment](set-containment.md)
* [vector-containment](vector-containment.md)



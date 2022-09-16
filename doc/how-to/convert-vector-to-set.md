<!---
  This markdown file was generated. Do not edit.
  -->

## Convert a vector into a set

A vector can be converted into a set via 'concat'.

Combine a vector into an empty set to effectively convert the vector into a set which contains the same elements.

```clojure
(let [v [10 20 30]]
  (concat #{} v))


;-- result --
#{10 20 30}
```

Note that duplicate elements are removed in the process.

```clojure
(let [v [10 10 20 30]]
  (concat #{} v))


;-- result --
#{10 20 30}
```

#### Basic elements:

[`set`](../halite-basic-syntax-reference.md#set), [`vector`](../halite-basic-syntax-reference.md#vector)

#### Operator reference:

* [`concat`](../halite-full-reference.md#concat)


#### See also:

* [convert-set-to-vector](convert-set-to-vector.md)
* [remove-duplicates-from-vector](remove-duplicates-from-vector.md)



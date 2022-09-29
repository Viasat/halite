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

### Reference

#### Basic elements:

[`set`](../halite_basic-syntax-reference.md#set), [`vector`](../halite_basic-syntax-reference.md#vector)

#### Operator reference:

* [`concat`](../halite_full-reference.md#concat)


#### How Tos:

* [convert-set-to-vector](../how-to/halite_convert-set-to-vector.md)
* [remove-duplicates-from-vector](../how-to/halite_remove-duplicates-from-vector.md)



<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite set-out reference

### <a name="set-out"></a>set-out

Operations that produce sets.

For basic syntax of this data type see: [`set`](jadeite-basic-syntax-reference.md#set)

!["set-out"](./halite-bnf-diagrams/set-out-j.svg)

#### [`concat`](jadeite-full-reference.md#concat)

Combine two collections into one.

#### [`conj`](jadeite-full-reference.md#conj)

Add individual items to a collection.

#### [`difference`](jadeite-full-reference.md#difference)

Compute the set difference of two sets.

#### [`filter`](jadeite-full-reference.md#filter)

Produce a new collection which contains only the elements from the original collection for which the boolean-expression is true. When applied to a vector, the order of the elements in the result preserves the order from the original vector.

#### [`intersection`](jadeite-full-reference.md#intersection)

Compute the set intersection of the sets.

#### [`map`](jadeite-full-reference.md#map)

Produce a new collection from a collection by evaluating the expression with the symbol bound to each element of the original collection, one-by-one. The results of evaluating the expression will be in the resulting collection. When operating on a vector, the order of the output vector will correspond to the order of the items in the original vector.

#### [`union`](jadeite-full-reference.md#union)

Compute the union of all the sets.

---

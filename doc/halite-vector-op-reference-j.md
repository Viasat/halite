<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite reference: Vector operations

Operations that operate on vectors.

For basic syntax of this data type see: [`vector`](jadeite-basic-syntax-reference.md#vector)

!["vector-op"](./halite-bnf-diagrams/vector-op-j.svg)

#### [`!=`](jadeite-full-reference.md#_B_E)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

#### [`==`](jadeite-full-reference.md#_E_E)

Determine if two values are equivalent. For vectors and sets this performs a comparison of their contents.

#### [`ACCESSOR`](jadeite-full-reference.md#ACCESSOR)

Extract the given item from the first argument. If the first argument is an instance, extract the value for the given field from the given instance. For optional fields, this may produce 'unset'. Otherwise this will always produce a value. If the first argument is a vector, then extract the value at the given index in the vector. The index in this case is zero based.

#### [`ACCESSOR-CHAIN`](jadeite-full-reference.md#ACCESSOR-CHAIN)

A path of element accessors can be created by chaining together element access forms in sequence.

#### [`any?`](jadeite-full-reference.md#any_Q)

Evaluates to true if the boolean-expression is true when the symbol is bound to some element in the collection.

#### [`concat`](jadeite-full-reference.md#concat)

Combine two collections into one.

#### [`conj`](jadeite-full-reference.md#conj)

Add individual items to a collection.

#### [`count`](jadeite-full-reference.md#count)

Return how many items are in a collection.

#### [`equalTo`](jadeite-full-reference.md#equalTo)

Determine if two values are equivalent. For vectors and sets this performs a comparison of their contents.

#### [`every?`](jadeite-full-reference.md#every_Q)

Evaluates to true if the boolean-expression is true when the symbol is bound to each the element in the collection.

#### [`filter`](jadeite-full-reference.md#filter)

Produce a new collection which contains only the elements from the original collection for which the boolean-expression is true. When applied to a vector, the order of the elements in the result preserves the order from the original vector.

#### [`first`](jadeite-full-reference.md#first)

Produce the first element from a vector.

#### [`map`](jadeite-full-reference.md#map)

Produce a new collection from a collection by evaluating the expression with the symbol bound to each element of the original collection, one-by-one. The results of evaluating the expression will be in the resulting collection. When operating on a vector, the order of the output vector will correspond to the order of the items in the original vector.

#### [`notEqualTo`](jadeite-full-reference.md#notEqualTo)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

#### [`reduce`](jadeite-full-reference.md#reduce)

Evalue the expression repeatedly for each element in the vector. The accumulator value will have a value of accumulator-init on the first evaluation of the expression. Subsequent evaluations of the expression will chain the prior result in as the value of the accumulator. The result of the final evaluation of the expression will be produced as the result of the reduce operation. The elements are processed in order.

#### [`rest`](jadeite-full-reference.md#rest)

Produce a new vector which contains the same element of the argument, in the same order, except the first element is removed. If there are no elements in the argument, then an empty vector is produced.

#### [`sort`](jadeite-full-reference.md#sort)

Produce a new vector by sorting all of the items in the argument. Only collections of numeric values may be sorted.

#### [`sortBy`](jadeite-full-reference.md#sortBy)

Produce a new vector by sorting all of the items in the input collection according to the values produced by applying the expression to each element. The expression must produce a unique, sortable value for each element.

---

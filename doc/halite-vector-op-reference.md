<!---
  This markdown file was generated. Do not edit.
  -->

# Halite vector-op reference

### <a name="vector-op"></a>vector-op

Operations that operate on vectors.

For basic syntax of this data type see: [`vector`](halite-basic-syntax-reference.md#vector)

!["vector-op"](./halite-bnf-diagrams/vector-op.svg)

#### [`=`](halite-full-reference.md#_E)

Determine if two values are equivalent. For vectors and sets this performs a comparison of their contents.

#### [`any?`](halite-full-reference.md#any_Q)

Evaluates to true if the boolean-expression is true when the symbol is bound to some element in the collection.

#### [`concat`](halite-full-reference.md#concat)

Combine two collections into one.

#### [`conj`](halite-full-reference.md#conj)

Add individual items to a collection.

#### [`count`](halite-full-reference.md#count)

Return how many items are in a collection.

#### [`every?`](halite-full-reference.md#every_Q)

Evaluates to true if the boolean-expression is true when the symbol is bound to each the element in the collection.

#### [`filter`](halite-full-reference.md#filter)

Produce a new collection which contains only the elements from the original collection for which the boolean-expression is true. When applied to a vector, the order of the elements in the result preserves the order from the original vector.

#### [`first`](halite-full-reference.md#first)

Produce the first element from a vector.

#### [`get`](halite-full-reference.md#get)

Extract the given item from the first argument. If the first argument is an instance, extract the value for the given field from the given instance. For optional fields, this may produce 'unset'. Otherwise this will always produce a value. If the first argument is a vector, then extract the value at the given index in the vector. The index in this case is zero based.

#### [`get-in`](halite-full-reference.md#get-in)

Syntactic sugar for performing the equivalent of a chained series of 'get' operations. The second argument is a vector that represents the logical path to be navigated through the first argument.

#### [`map`](halite-full-reference.md#map)

Produce a new collection from a collection by evaluating the expression with the symbol bound to each element of the original collection, one-by-one. The results of evaluating the expression will be in the resulting collection. When operating on a vector, the order of the output vector will correspond to the order of the items in the original vector.

#### [`not=`](halite-full-reference.md#not_E)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

#### [`reduce`](halite-full-reference.md#reduce)

Evalue the expression repeatedly for each element in the vector. The accumulator value will have a value of accumulator-init on the first evaluation of the expression. Subsequent evaluations of the expression will chain the prior result in as the value of the accumulator. The result of the final evaluation of the expression will be produced as the result of the reduce operation. The elements are processed in order.

#### [`rest`](halite-full-reference.md#rest)

Produce a new vector which contains the same element of the argument, in the same order, except the first element is removed. If there are no elements in the argument, then an empty vector is produced.

#### [`sort`](halite-full-reference.md#sort)

Produce a new vector by sorting all of the items in the argument. Only collections of numeric values may be sorted.

#### [`sort-by`](halite-full-reference.md#sort-by)

Produce a new vector by sorting all of the items in the input collection according to the values produced by applying the expression to each element. The expression must produce a unique, sortable value for each element.

---

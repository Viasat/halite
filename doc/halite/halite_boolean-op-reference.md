<!---
  This markdown file was generated. Do not edit.
  -->

# Halite reference: Boolean operations

Operations that operate on boolean values.

For basic syntax of this data type see: [`boolean`](halite_basic-syntax-reference.md#boolean)

!["boolean-op"](../halite-bnf-diagrams/boolean-op.svg)

#### [`=`](halite_full-reference.md#_E)

Determine if values are equivalent. For vectors and sets this performs a comparison of their contents.

#### [`=>`](halite_full-reference.md#_E_G)

Performs logical implication. If the first value is true, then the second value must also be true for the result to be true. If the first value is false, then the result is true.

#### [`and`](halite_full-reference.md#and)

Perform a logical 'and' operation on the input values.

#### [`if`](halite_full-reference.md#if)

If the first argument is true, then evaluate the second argument, otherwise evaluate the third argument.

#### [`not`](halite_full-reference.md#not)

Performs logical negation of the argument.

#### [`not=`](halite_full-reference.md#not_E)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

#### [`or`](halite_full-reference.md#or)

Perform a logical 'or' operation on the input values.

#### [`when`](halite_full-reference.md#when)

If the first argument is true, then evaluate the second argument, otherwise produce 'unset'.

---

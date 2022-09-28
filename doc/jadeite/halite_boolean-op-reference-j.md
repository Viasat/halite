<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite reference: Boolean operations

Operations that operate on boolean values.

For basic syntax of this data type see: [`boolean`](halite_basic-syntax-reference-j.md#boolean)

!["boolean-op"](./halite-bnf-diagrams/boolean-op-j.svg)

#### [`!`](halite_full-reference-j.md#_B)

Performs logical negation of the argument.

#### [`!=`](halite_full-reference-j.md#_B_E)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

#### [`&&`](halite_full-reference-j.md#&&)

Perform a logical 'and' operation on the input values.

#### [`==`](halite_full-reference-j.md#_E_E)

Determine if values are equivalent. For vectors and sets this performs a comparison of their contents.

#### [`=>`](halite_full-reference-j.md#_E_G)

Performs logical implication. If the first value is true, then the second value must also be true for the result to be true. If the first value is false, then the result is true.

#### [`equalTo`](halite_full-reference-j.md#equalTo)

Determine if values are equivalent. For vectors and sets this performs a comparison of their contents.

#### [`if`](halite_full-reference-j.md#if)

If the first argument is true, then evaluate the second argument, otherwise evaluate the third argument.

#### [`notEqualTo`](halite_full-reference-j.md#notEqualTo)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

#### [`when`](halite_full-reference-j.md#when)

If the first argument is true, then evaluate the second argument, otherwise produce 'unset'.

#### [`||`](halite_full-reference-j.md#||)

Perform a logical 'or' operation on the input values.

---

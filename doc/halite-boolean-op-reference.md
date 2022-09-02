<!---
  This markdown file was generated. Do not edit.
  -->

# Halite boolean-op reference

### <a name="boolean-op"></a>boolean-op

Operations that operate on boolean values.

!["boolean-op"](./halite-bnf-diagrams/boolean-op.svg)

#### [`=>`](halite-full-reference.md#_E_G)

Performs logical implication. If the first value is true, then the second value must also be true for the result to be true. If the first value is false, then the result is true.

#### [`and`](halite-full-reference.md#and)

Perform a logical 'and' operation on the input values.

#### [`if`](halite-full-reference.md#if)

If the first argument is true, then evaluate the second argument, otherwise evaluate the third argument.

#### [`not`](halite-full-reference.md#not)

Performs logical negation of the argument.

#### [`not=`](halite-full-reference.md#not_E)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

#### [`or`](halite-full-reference.md#or)

Perform a logical 'or' operation on the input values.

#### [`when`](halite-full-reference.md#when)

If the first argument is true, then evaluate the second argument, otherwise produce 'unset'.

---

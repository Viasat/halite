<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite boolean-op reference

### <a name="boolean-op"></a>boolean-op

Operations that operate on boolean values.

For basic syntax of this data type see: [`boolean`](jadeite-basic-syntax-reference.md#boolean)

!["boolean-op"](./halite-bnf-diagrams/boolean-op-j.svg)

#### [`!`](jadeite-full-reference.md#_B)

Performs logical negation of the argument.

#### [`&&`](jadeite-full-reference.md#&&)

Perform a logical 'and' operation on the input values.

#### [`=>`](jadeite-full-reference.md#_E_G)

Performs logical implication. If the first value is true, then the second value must also be true for the result to be true. If the first value is false, then the result is true.

#### [`if`](jadeite-full-reference.md#if)

If the first argument is true, then evaluate the second argument, otherwise evaluate the third argument.

#### [`notEqualTo`](jadeite-full-reference.md#notEqualTo)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

#### [`when`](jadeite-full-reference.md#when)

If the first argument is true, then evaluate the second argument, otherwise produce 'unset'.

#### [`||`](jadeite-full-reference.md#||)

Perform a logical 'or' operation on the input values.

---

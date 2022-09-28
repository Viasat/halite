<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite reference: Produce booleans

Operations that produce boolean output values.

For basic syntax of this data type see: [`boolean`](halite_basic-syntax-reference-j.md#boolean)

!["boolean-out"](./halite-bnf-diagrams/boolean-out-j.svg)

#### [`!`](halite_full-reference-j.md#_B)

Performs logical negation of the argument.

#### [`!=`](halite_full-reference-j.md#_B_E)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

#### [`&&`](halite_full-reference-j.md#&&)

Perform a logical 'and' operation on the input values.

#### [`<`](halite_full-reference-j.md#_L)

Determine if a number is strictly less than another.

#### [`<=`](halite_full-reference-j.md#_L_E)

Determine if a number is less than or equal to another.

#### [`==`](halite_full-reference-j.md#_E_E)

Determine if values are equivalent. For vectors and sets this performs a comparison of their contents.

#### [`=>`](halite_full-reference-j.md#_E_G)

Performs logical implication. If the first value is true, then the second value must also be true for the result to be true. If the first value is false, then the result is true.

#### [`>`](halite_full-reference-j.md#_G)

Determine if a number is strictly greater than another.

#### [`>=`](halite_full-reference-j.md#_G_E)

Determine if a number is greater than or equal to another.

#### [`any?`](halite_full-reference-j.md#any_Q)

Evaluates to true if the boolean-expression is true when the symbol is bound to some element in the collection.

#### [`contains?`](halite_full-reference-j.md#contains_Q)

Determine if a specific value is in a set.

#### [`equalTo`](halite_full-reference-j.md#equalTo)

Determine if values are equivalent. For vectors and sets this performs a comparison of their contents.

#### [`every?`](halite_full-reference-j.md#every_Q)

Evaluates to true if the boolean-expression is true when the symbol is bound to each the element in the collection.

#### [`notEqualTo`](halite_full-reference-j.md#notEqualTo)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

#### [`refinesTo?`](halite_full-reference-j.md#refinesTo_Q)

Determine whether it is possible to refine the given instance into an instance of type, spec-id.

#### [`subset?`](halite_full-reference-j.md#subset_Q)

Return false if there are any items in the first set which do not appear in the second set. Otherwise return true.

#### [`valid?`](halite_full-reference-j.md#valid_Q)

Evaluate the instance expression and produce false if a constraint violation occurs during the evaluation. Otherwise, produce true.

#### [`||`](halite_full-reference-j.md#||)

Perform a logical 'or' operation on the input values.

---

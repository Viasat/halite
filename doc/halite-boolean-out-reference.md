<!---
  This markdown file was generated. Do not edit.
  -->

# Halite reference: Produce booleans

### <a name="boolean-out"></a>Operations that produce boolean output values.

For basic syntax of this data type see: [`boolean`](halite-basic-syntax-reference.md#boolean)

!["boolean-out"](./halite-bnf-diagrams/boolean-out.svg)

#### [`<`](halite-full-reference.md#_L)

Determine if a number is strictly less than another.

#### [`<=`](halite-full-reference.md#_L_E)

Determine if a number is less than or equal to another.

#### [`=`](halite-full-reference.md#_E)

Determine if two values are equivalent. For vectors and sets this performs a comparison of their contents.

#### [`=>`](halite-full-reference.md#_E_G)

Performs logical implication. If the first value is true, then the second value must also be true for the result to be true. If the first value is false, then the result is true.

#### [`>`](halite-full-reference.md#_G)

Determine if a number is strictly greater than another.

#### [`>=`](halite-full-reference.md#_G_E)

Determine if a number is greater than or equal to another.

#### [`and`](halite-full-reference.md#and)

Perform a logical 'and' operation on the input values.

#### [`any?`](halite-full-reference.md#any_Q)

Evaluates to true if the boolean-expression is true when the symbol is bound to some element in the collection.

#### [`contains?`](halite-full-reference.md#contains_Q)

Determine if a specific value is in a set.

#### [`every?`](halite-full-reference.md#every_Q)

Evaluates to true if the boolean-expression is true when the symbol is bound to each the element in the collection.

#### [`not`](halite-full-reference.md#not)

Performs logical negation of the argument.

#### [`not=`](halite-full-reference.md#not_E)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

#### [`or`](halite-full-reference.md#or)

Perform a logical 'or' operation on the input values.

#### [`refines-to?`](halite-full-reference.md#refines-to_Q)

Determine whether it is possible to refine the given instance into an instance of type, spec-id.

#### [`subset?`](halite-full-reference.md#subset_Q)

Return false if there are any items in the first set which do not appear in the second set. Otherwise return true.

#### [`valid?`](halite-full-reference.md#valid_Q)

Evaluate the instance expression and produce false if a constraint violation occurs during the evaluation. Otherwise, produce true.

---

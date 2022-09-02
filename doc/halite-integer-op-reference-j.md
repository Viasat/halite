<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite integer-op reference

### <a name="integer-op"></a>integer-op

Operations that operate on integer values.

For basic syntax of this data type see: [`integer`](jadeite-basic-syntax-reference.md#integer)

!["integer-op"](./halite-bnf-diagrams/integer-op-j.svg)

#### [`%`](jadeite-full-reference.md#%)

Computes the mathematical modulus of two numbers. Use care if one of the arguments is negative.

#### [`*`](jadeite-full-reference.md#_S)

Multiply two numbers together.

#### [`+`](jadeite-full-reference.md#_A)

Add two numbers together.

#### [`-`](jadeite-full-reference.md#-)

Subtract one number from another.

#### [`/`](jadeite-full-reference.md#/)

Divide the first number by the second. When the first argument is an integer the result is truncated to an integer value. When the first argument is a fixed-decimal the result is truncated to the same precision as the first argument.

#### [`<`](jadeite-full-reference.md#_L)

Determine if a number is strictly less than another.

#### [`<=`](jadeite-full-reference.md#_L_E)

Determine if a number is less than or equal to another.

#### [`>`](jadeite-full-reference.md#_G)

Determine if a number is strictly greater than another.

#### [`>=`](jadeite-full-reference.md#_G_E)

Determine if a number is greater than or equal to another.

#### [`abs`](jadeite-full-reference.md#abs)

Compute the absolute value of a number.

#### [`dec`](jadeite-full-reference.md#dec)

Decrement a numeric value.

#### [`equalTo`](jadeite-full-reference.md#equalTo)

Determine if two values are equivalent. For vectors and sets this performs a comparison of their contents.

#### [`expt`](jadeite-full-reference.md#expt)

Compute the numeric result of raising the first argument to the power given by the second argument. The exponent argument cannot be negative.

#### [`inc`](jadeite-full-reference.md#inc)

Increment a numeric value.

#### [`notEqualTo`](jadeite-full-reference.md#notEqualTo)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

---

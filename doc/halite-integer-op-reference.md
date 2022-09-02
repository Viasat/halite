<!---
  This markdown file was generated. Do not edit.
  -->

# Halite integer-op reference

### <a name="integer-op"></a>integer-op

Operations that operate on integer values.

For basic syntax of this data type see: [`integer`](halite-basic-syntax-reference.md#integer)

!["integer-op"](./halite-bnf-diagrams/integer-op.svg)

#### [`*`](halite-full-reference.md#_S)

Multiply two numbers together.

#### [`+`](halite-full-reference.md#_A)

Add two numbers together.

#### [`-`](halite-full-reference.md#-)

Subtract one number from another.

#### [`<`](halite-full-reference.md#_L)

Determine if a number is strictly less than another.

#### [`<=`](halite-full-reference.md#_L_E)

Determine if a number is less than or equal to another.

#### [`=`](halite-full-reference.md#_E)

Determine if two values are equivalent. For vectors and sets this performs a comparison of their contents.

#### [`>`](halite-full-reference.md#_G)

Determine if a number is strictly greater than another.

#### [`>=`](halite-full-reference.md#_G_E)

Determine if a number is greater than or equal to another.

#### [`abs`](halite-full-reference.md#abs)

Compute the absolute value of a number.

#### [`dec`](halite-full-reference.md#dec)

Decrement a numeric value.

#### [`div`](halite-full-reference.md#div)

Divide the first number by the second. When the first argument is an integer the result is truncated to an integer value. When the first argument is a fixed-decimal the result is truncated to the same precision as the first argument.

#### [`expt`](halite-full-reference.md#expt)

Compute the numeric result of raising the first argument to the power given by the second argument. The exponent argument cannot be negative.

#### [`inc`](halite-full-reference.md#inc)

Increment a numeric value.

#### [`mod`](halite-full-reference.md#mod)

Computes the mathematical modulus of two numbers. Use care if one of the arguments is negative.

#### [`not=`](halite-full-reference.md#not_E)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

---

<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite integer-out reference

### <a name="integer-out"></a>integer-out

Operations that produce integer output values.

For basic syntax of this data type see: [`integer`](jadeite-basic-syntax-reference.md#integer)

!["integer-out"](./halite-bnf-diagrams/integer-out-j.svg)

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

#### [`abs`](jadeite-full-reference.md#abs)

Compute the absolute value of a number.

#### [`count`](jadeite-full-reference.md#count)

Return how many items are in a collection.

#### [`dec`](jadeite-full-reference.md#dec)

Decrement a numeric value.

#### [`expt`](jadeite-full-reference.md#expt)

Compute the numeric result of raising the first argument to the power given by the second argument. The exponent argument cannot be negative.

#### [`inc`](jadeite-full-reference.md#inc)

Increment a numeric value.

#### [`rescale`](jadeite-full-reference.md#rescale)

Produce a number by adjusting the scale of the fixed-decimal to the new-scale. If the scale is being reduced, the original number is truncated. If the scale is being increased, then the original number is padded with zeroes in the decimal places. If the new-scale is zero, then the result is an integer.

---

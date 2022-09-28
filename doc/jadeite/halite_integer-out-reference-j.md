<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite reference: Produce integer

Operations that produce integer output values.

For basic syntax of this data type see: [`integer`](halite_basic-syntax-reference-j.md#integer)

!["integer-out"](./halite-bnf-diagrams/integer-out-j.svg)

#### [`%`](halite_full-reference-j.md#%)

Computes the mathematical modulus of two numbers. Use care if one of the arguments is negative.

#### [`*`](halite_full-reference-j.md#_S)

Multiply two numbers together.

#### [`+`](halite_full-reference-j.md#_A)

Add two numbers together.

#### [`-`](halite_full-reference-j.md#-)

Subtract one number from another.

#### [`/`](halite_full-reference-j.md#/)

Divide the first number by the second. When the first argument is an integer the result is truncated to an integer value. When the first argument is a fixed-decimal the result is truncated to the same precision as the first argument.

#### [`abs`](halite_full-reference-j.md#abs)

Compute the absolute value of a number.

#### [`count`](halite_full-reference-j.md#count)

Return how many items are in a collection.

#### [`dec`](halite_full-reference-j.md#dec)

Decrement a numeric value.

#### [`expt`](halite_full-reference-j.md#expt)

Compute the numeric result of raising the first argument to the power given by the second argument. The exponent argument cannot be negative.

#### [`inc`](halite_full-reference-j.md#inc)

Increment a numeric value.

#### [`rescale`](halite_full-reference-j.md#rescale)

Produce a number by adjusting the scale of the fixed-decimal to the new-scale. If the scale is being reduced, the original number is truncated. If the scale is being increased, then the original number is padded with zeroes in the decimal places. If the new-scale is zero, then the result is an integer.

---

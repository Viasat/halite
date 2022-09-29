<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite reference: Produce fixed-decimals

Operations that produce fixed-decimal output values.

For basic syntax of this data type see: [`fixed-decimal`](halite_basic-syntax-reference-j.md#fixed-decimal)

!["fixed-decimal-out"](../halite-bnf-diagrams/fixed-decimal-out-j.svg)

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

#### [`rescale`](halite_full-reference-j.md#rescale)

Produce a number by adjusting the scale of the fixed-decimal to the new-scale. If the scale is being reduced, the original number is truncated. If the scale is being increased, then the original number is padded with zeroes in the decimal places. If the new-scale is zero, then the result is an integer.

---

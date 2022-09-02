<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite fixed-decimal-out reference

### <a name="fixed-decimal-out"></a>fixed-decimal-out

Operations that produce fixed-decimal output values.

!["fixed-decimal-out"](./halite-bnf-diagrams/fixed-decimal-out-j.svg)

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

#### [`rescale`](jadeite-full-reference.md#rescale)

Produce a number by adjusting the scale of the fixed-decimal to the new-scale. If the scale is being reduced, the original number is truncated. If the scale is being increased, then the original number is padded with zeroes in the decimal places. If the new-scale is zero, then the result is an integer.

---

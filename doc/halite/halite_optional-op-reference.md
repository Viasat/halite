<!---
  This markdown file was generated. Do not edit.
  -->

# Halite reference: Optional operations

Operations that operate on optional fields and optional values in general.

!["optional-op"](../halite-bnf-diagrams/optional-op.svg)

#### [`=`](halite_full-reference.md#_E)

Determine if values are equivalent. For vectors and sets this performs a comparison of their contents.

#### [`if-value`](halite_full-reference.md#if-value)

Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then evaluate the third argument.

#### [`if-value-let`](halite_full-reference.md#if-value-let)

If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then evaluate the third argument without introducing a new binding for the symbol.

#### [`not=`](halite_full-reference.md#not_E)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

#### [`when-value`](halite_full-reference.md#when-value)

Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then produce unset.

#### [`when-value-let`](halite_full-reference.md#when-value-let)

If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then produce 'unset'

---

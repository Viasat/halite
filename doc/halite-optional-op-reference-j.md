<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite reference: Optional operations

### <a name="optional-op"></a>Operations that operate on optional fields and optional values in general.

!["optional-op"](./halite-bnf-diagrams/optional-op-j.svg)

#### [`ifValue`](jadeite-full-reference.md#ifValue)

Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then evaluate the third argument.

#### [`ifValueLet`](jadeite-full-reference.md#ifValueLet)

If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then evaluate the third argument without introducing a new binding for the symbol.

#### [`whenValue`](jadeite-full-reference.md#whenValue)

Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then produce unset.

#### [`whenValueLet`](jadeite-full-reference.md#whenValueLet)

If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then produce 'unset'

---

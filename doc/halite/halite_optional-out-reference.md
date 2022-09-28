<!---
  This markdown file was generated. Do not edit.
  -->

# Halite reference: Optionally produce values

Operations that produce optional values.

!["optional-out"](./halite-bnf-diagrams/optional-out.svg)

#### [`$no-value`](halite_full-reference.md#_Dno-value)

Constant that produces the special 'unset' value which represents the lack of a value.

#### [`get`](halite_full-reference.md#get)

Extract the given item from the first argument. If the first argument is an instance, extract the value for the given field from the given instance. For optional fields, this may produce 'unset'. Otherwise this will always produce a value. If the first argument is a vector, then extract the value at the given index in the vector. The index in this case is zero based.

#### [`get-in`](halite_full-reference.md#get-in)

Syntactic sugar for performing the equivalent of a chained series of 'get' operations. The second argument is a vector that represents the logical path to be navigated through the first argument.

#### [`valid`](halite_full-reference.md#valid)

Evaluate the instance-expression and produce the result. If a constraint violation occurs while evaluating the expression then produce an 'unset' value.

#### [`when`](halite_full-reference.md#when)

If the first argument is true, then evaluate the second argument, otherwise produce 'unset'.

#### [`when-value`](halite_full-reference.md#when-value)

Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then produce unset.

#### [`when-value-let`](halite_full-reference.md#when-value-let)

If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then produce 'unset'

---

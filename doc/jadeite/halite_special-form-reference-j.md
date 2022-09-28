<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite reference: Special forms

Operators that do not evaluate their arguments in the 'normal' way.

!["special-form"](./halite-bnf-diagrams/special-form-j.svg)

#### [`ACCESSOR`](halite_full-reference-j.md#ACCESSOR)

Extract the given item from the first argument. If the first argument is an instance, extract the value for the given field from the given instance. For optional fields, this may produce 'unset'. Otherwise this will always produce a value. If the first argument is a vector, then extract the value at the given index in the vector. The index in this case is zero based.

#### [`ACCESSOR-CHAIN`](halite_full-reference-j.md#ACCESSOR-CHAIN)

A path of element accessors can be created by chaining together element access forms in sequence.

#### [`any?`](halite_full-reference-j.md#any_Q)

Evaluates to true if the boolean-expression is true when the symbol is bound to some element in the collection.

#### [`every?`](halite_full-reference-j.md#every_Q)

Evaluates to true if the boolean-expression is true when the symbol is bound to each the element in the collection.

#### [`filter`](halite_full-reference-j.md#filter)

Produce a new collection which contains only the elements from the original collection for which the boolean-expression is true. When applied to a vector, the order of the elements in the result preserves the order from the original vector.

#### [`if`](halite_full-reference-j.md#if)

If the first argument is true, then evaluate the second argument, otherwise evaluate the third argument.

#### [`ifValue`](halite_full-reference-j.md#ifValue)

Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then evaluate the third argument.

#### [`ifValueLet`](halite_full-reference-j.md#ifValueLet)

If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then evaluate the third argument without introducing a new binding for the symbol.

#### [`let`](halite_full-reference-j.md#let)

Evaluate the expression argument in a nested context created by binding each symbol to the corresponding value.

#### [`map`](halite_full-reference-j.md#map)

Produce a new collection from a collection by evaluating the expression with the symbol bound to each element of the original collection, one-by-one. The results of evaluating the expression will be in the resulting collection. When operating on a vector, the order of the output vector will correspond to the order of the items in the original vector.

#### [`reduce`](halite_full-reference-j.md#reduce)

Evalue the expression repeatedly for each element in the vector. The accumulator value will have a value of accumulator-init on the first evaluation of the expression. Subsequent evaluations of the expression will chain the prior result in as the value of the accumulator. The result of the final evaluation of the expression will be produced as the result of the reduce operation. The elements are processed in order.

#### [`sortBy`](halite_full-reference-j.md#sortBy)

Produce a new vector by sorting all of the items in the input collection according to the values produced by applying the expression to each element. The expression must produce a unique, sortable value for each element.

#### [`valid`](halite_full-reference-j.md#valid)

Evaluate the instance-expression and produce the result. If a constraint violation occurs while evaluating the expression then produce an 'unset' value.

#### [`valid?`](halite_full-reference-j.md#valid_Q)

Evaluate the instance expression and produce false if a constraint violation occurs during the evaluation. Otherwise, produce true.

#### [`when`](halite_full-reference-j.md#when)

If the first argument is true, then evaluate the second argument, otherwise produce 'unset'.

#### [`whenValue`](halite_full-reference-j.md#whenValue)

Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then produce unset.

#### [`whenValueLet`](halite_full-reference-j.md#whenValueLet)

If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then produce 'unset'

---

<!---
  This markdown file was generated. Do not edit.
  -->

# Halite reference: Instance operations

Operations that operate on spec instances.

For basic syntax of this data type see: [`instance`](halite-basic-syntax-reference.md#instance)

!["instance-op"](./halite-bnf-diagrams/instance-op.svg)

#### [`=`](halite-full-reference.md#_E)

Determine if values are equivalent. For vectors and sets this performs a comparison of their contents.

#### [`get`](halite-full-reference.md#get)

Extract the given item from the first argument. If the first argument is an instance, extract the value for the given field from the given instance. For optional fields, this may produce 'unset'. Otherwise this will always produce a value. If the first argument is a vector, then extract the value at the given index in the vector. The index in this case is zero based.

#### [`get-in`](halite-full-reference.md#get-in)

Syntactic sugar for performing the equivalent of a chained series of 'get' operations. The second argument is a vector that represents the logical path to be navigated through the first argument.

#### [`not=`](halite-full-reference.md#not_E)

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

#### [`refine-to`](halite-full-reference.md#refine-to)

Attempt to refine the given instance into an instance of type, spec-id.

#### [`refines-to?`](halite-full-reference.md#refines-to_Q)

Determine whether it is possible to refine the given instance into an instance of type, spec-id.

#### [`valid`](halite-full-reference.md#valid)

Evaluate the instance-expression and produce the result. If a constraint violation occurs while evaluating the expression then produce an 'unset' value.

#### [`valid?`](halite-full-reference.md#valid_Q)

Evaluate the instance expression and produce false if a constraint violation occurs during the evaluation. Otherwise, produce true.

---

<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite reference: Instance field operations

Operations that operate on fields of spec-instances.

For basic syntax of this data type see: [`instance`](halite_basic-syntax-reference-j.md#instance)

!["instance-field-op"](../halite-bnf-diagrams/instance-field-op-j.svg)

#### [`ACCESSOR`](halite_full-reference-j.md#ACCESSOR)

Extract the given item from the first argument. If the first argument is an instance, extract the value for the given field from the given instance. For optional fields, this may produce 'unset'. Otherwise this will always produce a value. If the first argument is a vector, then extract the value at the given index in the vector. The index in this case is zero based.

#### [`ACCESSOR-CHAIN`](halite_full-reference-j.md#ACCESSOR-CHAIN)

A path of element accessors can be created by chaining together element access forms in sequence.

---

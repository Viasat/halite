<!---
  This markdown file was generated. Do not edit.
  -->

# Halite resource specifications

All features are available in both Halite (s-expression) syntax and Jadeite (C-like) syntax.

## Tutorials

### spec

* Model a grocery delivery business [(Halite)](halite/tutorial/halite_grocery.md) [(Jadeite)](jadeite/tutorial/halite_grocery-j.md)
  * Consider how to use specs to model some of the important details of a business that provides grocery delivery to subscribers.
* Model a sudokuo puzzle [(Halite)](halite/tutorial/halite_sudoku.md) [(Jadeite)](jadeite/tutorial/halite_sudoku-j.md)
  * Consider how to use specs to model a sudoku game.

## How-To Guides

### instance

* Compose instances [(Halite)](halite/how-to/halite_compose-instances.md) [(Jadeite)](jadeite/how-to/halite_compose-instances-j.md)
  * How to make specs which are the composition of other specs and how to make instances of those specs.
* Defining constraints on instance values [(Halite)](halite/how-to/halite_constrain-instances.md) [(Jadeite)](jadeite/how-to/halite_constrain-instances-j.md)
  * How to constrain the possible values for instance fields
* Defining multiple constraints on instance values [(Halite)](halite/how-to/halite_multi-constrain-instances.md) [(Jadeite)](jadeite/how-to/halite_multi-constrain-instances-j.md)
  * How to define multiple constraints in a spec
* Spec variables [(Halite)](halite/how-to/halite_spec-variables.md) [(Jadeite)](jadeite/how-to/halite_spec-variables-j.md)
  * How to model data fields in specifications.
* String as enumeration [(Halite)](halite/how-to/halite_string-enum.md) [(Jadeite)](jadeite/how-to/halite_string-enum-j.md)
  * How to model an enumeration as a string
* Use an instance as a function to compute a value [(Halite)](halite/how-to/halite_functions.md) [(Jadeite)](jadeite/how-to/halite_functions-j.md)
  * Consider there is some logic that needs to be reused in multiple contexts. How to package it up so that it can be reused?
* Use an instance as a predicate [(Halite)](halite/how-to/halite_predicate.md) [(Jadeite)](jadeite/how-to/halite_predicate-j.md)
  * Consider you need to evaluate an expression as a predicate, to determine if some values relate to each other properly.
* Variables with abstract types [(Halite)](halite/how-to/halite_abstract-variables.md) [(Jadeite)](jadeite/how-to/halite_abstract-variables-j.md)
  * How to use variables which are defined to be the type of abstract specs.
* Variables with abstract types used in refinements [(Halite)](halite/how-to/halite_abstract-variables-refinements.md) [(Jadeite)](jadeite/how-to/halite_abstract-variables-refinements-j.md)
  * How to use variables which are defined to be the type of abstract specs in refinements.

### refinement

* Arbitrary expression in refinements [(Halite)](halite/how-to/halite_arbitrary-expression-refinements.md) [(Jadeite)](jadeite/how-to/halite_arbitrary-expression-refinements-j.md)
  * How to write arbitrary expressions to convert instances.
* Converting instances between specs [(Halite)](halite/how-to/halite_convert-instances.md) [(Jadeite)](jadeite/how-to/halite_convert-instances-j.md)
  * How to convert an instance from one spec type to another.
* Converting instances between specs transitively [(Halite)](halite/how-to/halite_convert-instances-transitively.md) [(Jadeite)](jadeite/how-to/halite_convert-instances-transitively-j.md)
  * How to convert an instance from one spec type to another through an intermediate spec.
* Optionally converting instances between specs [(Halite)](halite/how-to/halite_optionally-convert-instances.md) [(Jadeite)](jadeite/how-to/halite_optionally-convert-instances-j.md)
  * Consider there are some cases where an instance can be converted to another spec, but other cases where it cannot be. Refinement expressions can include logic to optionally convert an instance.

### collections

* Add contents of a set to a vector [(Halite)](halite/how-to/halite_combine-set-to-vector.md) [(Jadeite)](jadeite/how-to/halite_combine-set-to-vector-j.md)
  * A set must be sorted into a vector before it can be appended onto another vector.
* Combine collections together [(Halite)](halite/how-to/halite_combine.md) [(Jadeite)](jadeite/how-to/halite_combine-j.md)
  * Consider you have two sets or vectors and need to combine them.
* Convert a set into a vector [(Halite)](halite/how-to/halite_convert-set-to-vector.md) [(Jadeite)](jadeite/how-to/halite_convert-set-to-vector-j.md)
  * A set can be converted into a vector by sorting it.
* Convert a vector into a set [(Halite)](halite/how-to/halite_convert-vector-to-set.md) [(Jadeite)](jadeite/how-to/halite_convert-vector-to-set-j.md)
  * A vector can be converted into a set via 'concat'.
* Determine if an item is in a set [(Halite)](halite/how-to/halite_set-containment.md) [(Jadeite)](jadeite/how-to/halite_set-containment-j.md)
  * How to determine if a given item is contained in a set?
* Determine if an item is in a vector [(Halite)](halite/how-to/halite_vector-containment.md) [(Jadeite)](jadeite/how-to/halite_vector-containment-j.md)
  * Consider that you have a vector and you need to know whether it contains a specific value.
* Determine if any item in a collection satisfies some criteria [(Halite)](halite/how-to/halite_any.md) [(Jadeite)](jadeite/how-to/halite_any-j.md)
  * How to determine if any item in a collection satisifies some criteria?
* Remove duplicate values from a vector. [(Halite)](halite/how-to/halite_remove-duplicates-from-vector.md) [(Jadeite)](jadeite/how-to/halite_remove-duplicates-from-vector-j.md)
  * A vector can be converted to a set and back to a vector to remove duplicates.
* Transform a collection [(Halite)](halite/how-to/halite_transform.md) [(Jadeite)](jadeite/how-to/halite_transform-j.md)
  * Consider that you have a collection of values and need to produce a collection of new values derived from the first.
* Transform a vector into a single value [(Halite)](halite/how-to/halite_reduce.md) [(Jadeite)](jadeite/how-to/halite_reduce-j.md)
  * Consider that you have a vector of values and you need to produce a single value that takes into account all of the values in the vector.

### flow-control

* How to use short-circuiting to avoid runtime errors. [(Halite)](halite/how-to/halite_short-circuiting.md) [(Jadeite)](jadeite/how-to/halite_short-circuiting-j.md)
  * Several operations can throw runtime errors. This includes mathematical overflow, division by 0, index out of bounds, invoking non-existent refinement paths, and construction of invalid instances. The question is, how to write code to avoid such runtime errors?
* How to write a loop [(Halite)](halite/how-to/halite_loop.md) [(Jadeite)](jadeite/how-to/halite_loop-j.md)
  * There is no explicit language construct to write a loop. So how to write one?

### number

* Add an integer value to a decimal value [(Halite)](halite/how-to/halite_add-integer-to-decimal.md) [(Jadeite)](jadeite/how-to/halite_add-integer-to-decimal-j.md)
  * Consider you have an integer and a decimal value and you need to add them together.
* Divide an integer to produce a decimal result [(Halite)](halite/how-to/halite_perform-non-integer-division.md) [(Jadeite)](jadeite/how-to/halite_perform-non-integer-division-j.md)
  * Consider you have an integer value and you want to divide it by another integer to produce a decimal result.

## Explanation

### spec

* Clarify terminology around refinements [(Halite)](halite/explanation/halite_refinement-terminology.md) [(Jadeite)](jadeite/explanation/halite_refinement-terminology-j.md)
  * The primary intent of a refinement is to be a mechanism to translate instances of more concrete specifications into more abstract specifications.
* Considering a spec as a predicate [(Halite)](halite/explanation/halite_specs-as-predicates.md) [(Jadeite)](jadeite/explanation/halite_specs-as-predicates-j.md)
  * A spec can be considered a giant predicate which when applied to a value returns 'true' if the value is a valid instance and 'false' or a runtime error otherwise.
* Refinements as general purpose functions [(Halite)](halite/explanation/halite_refinements-as-functions.md) [(Jadeite)](jadeite/explanation/halite_refinements-as-functions-j.md)
  * Refinements can be used as general purpose instance conversion functions.
* Specs are about modeling things [(Halite)](halite/explanation/halite_big-picture.md) [(Jadeite)](jadeite/explanation/halite_big-picture-j.md)
  * Specs are a general mechanism for modelling whatever is of interest.
* What constraints are implied by refinement? [(Halite)](halite/explanation/halite_refinement-implications.md) [(Jadeite)](jadeite/explanation/halite_refinement-implications-j.md)
  * Specs can be defined as refining other specs. When this is done what constraints are implied by the refinement?
* What is an abstract field? [(Halite)](halite/explanation/halite_abstract-field.md) [(Jadeite)](jadeite/explanation/halite_abstract-field-j.md)
  * If a field in a spec has a type of an abstract spec, then the field can hold values that refine to that abstract spec.
* What is an abstract spec? [(Halite)](halite/explanation/halite_abstract-spec.md) [(Jadeite)](jadeite/explanation/halite_abstract-spec-j.md)
  * An abstract spec defines instances which cannot be used in the construction of other instances.

### language

* Language is functional [(Halite)](halite/explanation/halite_functional.md) [(Jadeite)](jadeite/explanation/halite_functional-j.md)
  * Both Halite, and its alternate representation, Jadeite, are purely functional languages.
* The pseduo-value 'unset' is handled specially [(Halite)](halite/explanation/halite_unset.md) [(Jadeite)](jadeite/explanation/halite_unset-j.md)
  * The 'unset' value cannot be used in general and there are specific facilities for dealing with them when they are produced by an expression.

## Reference
* Basic Syntax and Types [(Halite)](halite/halite_basic-syntax-reference.md), [(Jadeite)](jadeite/halite_basic-syntax-reference-j.md)
* Specification Map Syntax [(Halite)](halite_spec-syntax-reference.md)
* All Operators (alphabetical) [(Halite)](halite/halite_full-reference.md), [(Jadeite)](jadeite/halite_full-reference-j.md)
* Error ID Reference [(Halite)](halite/halite_err-id-reference.md), [(Jadeite)](jadeite/halite_err-id-reference-j.md)

#### Operators grouped by tag:

* Control flow [(Halite)](halite/halite_control-flow-reference.md) [(Jadeite)](jadeite/halite_control-flow-reference-j.md)
* Special forms [(Halite)](halite/halite_special-form-reference.md) [(Jadeite)](jadeite/halite_special-form-reference-j.md)
<table><tr><th></th><th>field-op</th>
<th>op</th>
<th>out</th>
</tr><tr><th>boolean</th><td>

</td><td>

 [H](halite/halite_boolean-op-reference.md) [J](jadeite/halite_boolean-op-reference-j.md)
</td><td>

 [H](halite/halite_boolean-out-reference.md) [J](jadeite/halite_boolean-out-reference-j.md)
</td></tr><tr><th>fixed-decimal</th><td>

</td><td>

 [H](halite/halite_fixed-decimal-op-reference.md) [J](jadeite/halite_fixed-decimal-op-reference-j.md)
</td><td>

 [H](halite/halite_fixed-decimal-out-reference.md) [J](jadeite/halite_fixed-decimal-out-reference-j.md)
</td></tr><tr><th>instance</th><td>

 [H](halite/halite_instance-field-op-reference.md) [J](jadeite/halite_instance-field-op-reference-j.md)
</td><td>

 [H](halite/halite_instance-op-reference.md) [J](jadeite/halite_instance-op-reference-j.md)
</td><td>

 [H](halite/halite_instance-out-reference.md) [J](jadeite/halite_instance-out-reference-j.md)
</td></tr><tr><th>integer</th><td>

</td><td>

 [H](halite/halite_integer-op-reference.md) [J](jadeite/halite_integer-op-reference-j.md)
</td><td>

 [H](halite/halite_integer-out-reference.md) [J](jadeite/halite_integer-out-reference-j.md)
</td></tr><tr><th>nothing</th><td>

</td><td>

</td><td>

 [H](halite/halite_nothing-out-reference.md) [J](jadeite/halite_nothing-out-reference-j.md)
</td></tr><tr><th>optional</th><td>

</td><td>

 [H](halite/halite_optional-op-reference.md) [J](jadeite/halite_optional-op-reference-j.md)
</td><td>

 [H](halite/halite_optional-out-reference.md) [J](jadeite/halite_optional-out-reference-j.md)
</td></tr><tr><th>set</th><td>

</td><td>

 [H](halite/halite_set-op-reference.md) [J](jadeite/halite_set-op-reference-j.md)
</td><td>

 [H](halite/halite_set-out-reference.md) [J](jadeite/halite_set-out-reference-j.md)
</td></tr><tr><th>spec-id</th><td>

</td><td>

 [H](halite/halite_spec-id-op-reference.md) [J](jadeite/halite_spec-id-op-reference-j.md)
</td><td>

</td></tr><tr><th>string</th><td>

</td><td>

 [H](halite/halite_string-op-reference.md) [J](jadeite/halite_string-op-reference-j.md)
</td><td>

</td></tr><tr><th>vector</th><td>

</td><td>

 [H](halite/halite_vector-op-reference.md) [J](jadeite/halite_vector-op-reference-j.md)
</td><td>

 [H](halite/halite_vector-out-reference.md) [J](jadeite/halite_vector-out-reference-j.md)
</td></tr></table>


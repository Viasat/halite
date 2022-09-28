<!---
  This markdown file was generated. Do not edit.
  -->

# Halite resource specifications

All features are available in both Halite (s-expression) syntax and Jadeite (C-like) syntax.

## Tutorials

### spec

* Model a grocery delivery business [(Halite)](tutorial/grocery.md) [(Jadeite)](tutorial/grocery-j.md)
  * Consider how to use specs to model some of the important details of a business that provides grocery delivery to subscribers.
* Model a sudokuo puzzle [(Halite)](tutorial/sudoku.md) [(Jadeite)](tutorial/sudoku-j.md)
  * Consider how to use specs to model a sudoku game.

## How-To Guides

### instance

* Compose instances [(Halite)](how-to/compose-instances.md) [(Jadeite)](how-to/compose-instances-j.md)
  * How to make specs which are the composition of other specs and how to make instances of those specs.
* Defining constraints on instance values [(Halite)](how-to/constrain-instances.md) [(Jadeite)](how-to/constrain-instances-j.md)
  * How to constrain the possible values for instance fields
* Defining multiple constraints on instance values [(Halite)](how-to/multi-constrain-instances.md) [(Jadeite)](how-to/multi-constrain-instances-j.md)
  * How to define multiple constraints in a spec
* Spec variables [(Halite)](how-to/spec-variables.md) [(Jadeite)](how-to/spec-variables-j.md)
  * How to model data fields in specifications.
* String as enumeration [(Halite)](how-to/string-enum.md) [(Jadeite)](how-to/string-enum-j.md)
  * How to model an enumeration as a string
* Use an instance as a function to compute a value [(Halite)](how-to/functions.md) [(Jadeite)](how-to/functions-j.md)
  * Consider there is some logic that needs to be reused in multiple contexts. How to package it up so that it can be reused?
* Use an instance as a predicate [(Halite)](how-to/predicate.md) [(Jadeite)](how-to/predicate-j.md)
  * Consider you need to evaluate an expression as a predicate, to determine if some values relate to each other properly.
* Variables with abstract types [(Halite)](how-to/abstract-variables.md) [(Jadeite)](how-to/abstract-variables-j.md)
  * How to use variables which are defined to be the type of abstract specs.
* Variables with abstract types used in refinements [(Halite)](how-to/abstract-variables-refinements.md) [(Jadeite)](how-to/abstract-variables-refinements-j.md)
  * How to use variables which are defined to be the type of abstract specs in refinements.

### refinement

* Arbitrary expression in refinements [(Halite)](how-to/arbitrary-expression-refinements.md) [(Jadeite)](how-to/arbitrary-expression-refinements-j.md)
  * How to write arbitrary expressions to convert instances.
* Converting instances between specs [(Halite)](how-to/convert-instances.md) [(Jadeite)](how-to/convert-instances-j.md)
  * How to convert an instance from one spec type to another.
* Converting instances between specs transitively [(Halite)](how-to/convert-instances-transitively.md) [(Jadeite)](how-to/convert-instances-transitively-j.md)
  * How to convert an instance from one spec type to another through an intermediate spec.
* Optionally converting instances between specs [(Halite)](how-to/optionally-convert-instances.md) [(Jadeite)](how-to/optionally-convert-instances-j.md)
  * Consider there are some cases where an instance can be converted to another spec, but other cases where it cannot be. Refinement expressions can include logic to optionally convert an instance.

### collections

* Add contents of a set to a vector [(Halite)](how-to/combine-set-to-vector.md) [(Jadeite)](how-to/combine-set-to-vector-j.md)
  * A set must be sorted into a vector before it can be appended onto another vector.
* Combine collections together [(Halite)](how-to/combine.md) [(Jadeite)](how-to/combine-j.md)
  * Consider you have two sets or vectors and need to combine them.
* Convert a set into a vector [(Halite)](how-to/convert-set-to-vector.md) [(Jadeite)](how-to/convert-set-to-vector-j.md)
  * A set can be converted into a vector by sorting it.
* Convert a vector into a set [(Halite)](how-to/convert-vector-to-set.md) [(Jadeite)](how-to/convert-vector-to-set-j.md)
  * A vector can be converted into a set via 'concat'.
* Determine if an item is in a set [(Halite)](how-to/set-containment.md) [(Jadeite)](how-to/set-containment-j.md)
  * How to determine if a given item is contained in a set?
* Determine if an item is in a vector [(Halite)](how-to/vector-containment.md) [(Jadeite)](how-to/vector-containment-j.md)
  * Consider that you have a vector and you need to know whether it contains a specific value.
* Determine if any item in a collection satisfies some criteria [(Halite)](how-to/any.md) [(Jadeite)](how-to/any-j.md)
  * How to determine if any item in a collection satisifies some criteria?
* Remove duplicate values from a vector. [(Halite)](how-to/remove-duplicates-from-vector.md) [(Jadeite)](how-to/remove-duplicates-from-vector-j.md)
  * A vector can be converted to a set and back to a vector to remove duplicates.
* Transform a collection [(Halite)](how-to/transform.md) [(Jadeite)](how-to/transform-j.md)
  * Consider that you have a collection of values and need to produce a collection of new values derived from the first.
* Transform a vector into a single value [(Halite)](how-to/reduce.md) [(Jadeite)](how-to/reduce-j.md)
  * Consider that you have a vector of values and you need to produce a single value that takes into account all of the values in the vector.

### flow-control

* How to use short-circuiting to avoid runtime errors. [(Halite)](how-to/short-circuiting.md) [(Jadeite)](how-to/short-circuiting-j.md)
  * Several operations can throw runtime errors. This includes mathematical overflow, division by 0, index out of bounds, invoking non-existent refinement paths, and construction of invalid instances. The question is: how to write code to avoid such runtime errors?
* How to write a loop [(Halite)](how-to/loop.md) [(Jadeite)](how-to/loop-j.md)
  * There is no explicit language construct to write a loop. So how to write one?

### number

* Add an integer value to a decimal value [(Halite)](how-to/add-integer-to-decimal.md) [(Jadeite)](how-to/add-integer-to-decimal-j.md)
  * Consider you have an integer and a decimal value and you need to add them together.
* Divide an integer to produce a decimal result [(Halite)](how-to/perform-non-integer-division.md) [(Jadeite)](how-to/perform-non-integer-division-j.md)
  * Consider you have an integer value and you want to divide it by another integer to produce a decimal result.

## Explanation

### spec

* Clarify terminology around refinements [(Halite)](explanation/refinement-terminology.md) [(Jadeite)](explanation/refinement-terminology-j.md)
  * The primary intent of a refinement is to be a mechanism to translate instances of more concrete specifications into more abstract specifications.
* Considering a spec as a predicate [(Halite)](explanation/specs-as-predicates.md) [(Jadeite)](explanation/specs-as-predicates-j.md)
  * A spec can be considered a giant predicate which when applied to a value returns 'true' if the value is a valid instance and 'false' or a runtime error otherwise.
* Refinements as general purpose functions [(Halite)](explanation/refinements-as-functions.md) [(Jadeite)](explanation/refinements-as-functions-j.md)
  * Refinements can be used as general purpose instance conversion functions.
* Specs are about modeling things [(Halite)](explanation/big-picture.md) [(Jadeite)](explanation/big-picture-j.md)
  * Specs are a general mechanism for modelling whatever is of interest.
* What constraints are implied by refinement? [(Halite)](explanation/refinement-implications.md) [(Jadeite)](explanation/refinement-implications-j.md)
  * Specs can be defined as refining other specs. When this is done what constraints are implied by the refinement?
* What is an abstract field? [(Halite)](explanation/abstract-field.md) [(Jadeite)](explanation/abstract-field-j.md)
  * If a field in a spec has a type of an abstract spec, then the field can hold values that refine to that abstract spec.
* What is an abstract spec? [(Halite)](explanation/abstract-spec.md) [(Jadeite)](explanation/abstract-spec-j.md)
  * An abstract spec defines instances which cannot be used in the construction of other instances.

### language

* Language is functional [(Halite)](explanation/functional.md) [(Jadeite)](explanation/functional-j.md)
  * Both Halite, and its alternate representation, Jadeite, are purely functional languages.
* The pseduo-value 'unset' is handled specially [(Halite)](explanation/unset.md) [(Jadeite)](explanation/unset-j.md)
  * The 'unset' value cannot be used in general and there are specific facilities for dealing with them when they are produced by an expression.

## Reference


* Basic Syntax and Types [(Halite)](halite-basic-syntax-reference.md)         [(Jadeite)](jadeite-basic-syntax-reference.md)
* All Operators (alphabetical) [(Halite)](halite-full-reference.md) [(Jadeite)](jadeite-full-reference.md)
* Error ID Reference [(Halite)](halite-err-id-reference.md)         [(Jadeite)](jadeite-err-id-reference.md)

#### Operators grouped by tag:

* Control flow [(Halite)](halite-control-flow-reference.md) [(Jadeite)](halite-control-flow-reference-j.md)
* Special forms [(Halite)](halite-special-form-reference.md) [(Jadeite)](halite-special-form-reference-j.md)
<table><tr><th></th><th>field-op</th>
<th>op</th>
<th>out</th>
</tr><tr><th>boolean</th><td>

</td><td>

 [H](halite-boolean-op-reference.md) [J](halite-boolean-op-reference-j.md)
</td><td>

 [H](halite-boolean-out-reference.md) [J](halite-boolean-out-reference-j.md)
</td></tr><tr><th>fixed-decimal</th><td>

</td><td>

 [H](halite-fixed-decimal-op-reference.md) [J](halite-fixed-decimal-op-reference-j.md)
</td><td>

 [H](halite-fixed-decimal-out-reference.md) [J](halite-fixed-decimal-out-reference-j.md)
</td></tr><tr><th>instance</th><td>

 [H](halite-instance-field-op-reference.md) [J](halite-instance-field-op-reference-j.md)
</td><td>

 [H](halite-instance-op-reference.md) [J](halite-instance-op-reference-j.md)
</td><td>

 [H](halite-instance-out-reference.md) [J](halite-instance-out-reference-j.md)
</td></tr><tr><th>integer</th><td>

</td><td>

 [H](halite-integer-op-reference.md) [J](halite-integer-op-reference-j.md)
</td><td>

 [H](halite-integer-out-reference.md) [J](halite-integer-out-reference-j.md)
</td></tr><tr><th>nothing</th><td>

</td><td>

</td><td>

 [H](halite-nothing-out-reference.md) [J](halite-nothing-out-reference-j.md)
</td></tr><tr><th>optional</th><td>

</td><td>

 [H](halite-optional-op-reference.md) [J](halite-optional-op-reference-j.md)
</td><td>

 [H](halite-optional-out-reference.md) [J](halite-optional-out-reference-j.md)
</td></tr><tr><th>set</th><td>

</td><td>

 [H](halite-set-op-reference.md) [J](halite-set-op-reference-j.md)
</td><td>

 [H](halite-set-out-reference.md) [J](halite-set-out-reference-j.md)
</td></tr><tr><th>spec-id</th><td>

</td><td>

 [H](halite-spec-id-op-reference.md) [J](halite-spec-id-op-reference-j.md)
</td><td>

</td></tr><tr><th>string</th><td>

</td><td>

 [H](halite-string-op-reference.md) [J](halite-string-op-reference-j.md)
</td><td>

</td></tr><tr><th>vector</th><td>

</td><td>

 [H](halite-vector-op-reference.md) [J](halite-vector-op-reference-j.md)
</td><td>

 [H](halite-vector-out-reference.md) [J](halite-vector-out-reference-j.md)
</td></tr></table>


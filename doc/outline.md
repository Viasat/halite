# Halite resource specifications

All features are available in both Halite (s-expression) syntax and Jadeite (C-like) syntax.

## Tutorials

TBD

## How-To Guides

* Arbitrary Expression in Refinements [(Halite)](how-to/arbitrary-expression-refinements.md) [(Jadeite)](how-to/arbitrary-expression-refinements-j.md)
  * How to write arbitrary expressions to convert instances.
* Compose Instances [(Halite)](how-to/compose-instances.md) [(Jadeite)](how-to/compose-instances-j.md)
  * How to make specs which are the composition of other specs and how to make instances of those specs.
* Converting Instances Between Specs [(Halite)](how-to/convert-instances.md) [(Jadeite)](how-to/convert-instances-j.md)
  * How to convert an instance from one spec type to another.
* Converting Instances Between Specs Transitively [(Halite)](how-to/convert-instances-transitively.md) [(Jadeite)](how-to/convert-instances-transitively-j.md)
  * How to convert an instance from one spec type to another through an intermediate spec.
* Defining Constraints on Instance Values [(Halite)](how-to/constrain-instances.md) [(Jadeite)](how-to/constrain-instances-j.md)
  * How to constrain the possible values for instance fields
* Defining Multiple Constraints on Instance Values [(Halite)](how-to/multi-constrain-instances.md) [(Jadeite)](how-to/multi-constrain-instances-j.md)
  * How to define multiple constraints in a spec
* Optionally Converting Instances Between Specs [(Halite)](how-to/optionally-convert-instances.md) [(Jadeite)](how-to/optionally-convert-instances-j.md)
  * Refinement expressions can include logic to optionally convert an instance.
* Spec Variables [(Halite)](how-to/spec-variables.md) [(Jadeite)](how-to/spec-variables-j.md)
  * How to model data fields in specifications.
## Explanation

TBD

## Reference


* Basic Syntax [(Halite)](halite-basic-syntax-reference.md)         [(Jadeite)](jadeite-basic-syntax-reference.md)
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


A spec-map is a data structure used to define specs that are in context for evaluating some expressions.

Specs include variables which have types as:

![type](halite-bnf-diagrams/spec-syntax/type.svg)

The variables for a spec are defined in a spec-var-map:

![spec-var-map](halite-bnf-diagrams/spec-syntax/spec-var-map.svg)

Constraints on those variables are defined as:

![constraints](halite-bnf-diagrams/spec-syntax/constraints.svg)

Any applicable refinements are defined as:

![refinement-map](halite-bnf-diagrams/spec-syntax/refinement-map.svg)

All the specs in scope are packaged up into a spec-map:

![spec-map](halite-bnf-diagrams/spec-syntax/spec-map.svg)

Note, of course each key can only appear once in each map that defines a spec. The diagram shows it this way just so it is easier to read.
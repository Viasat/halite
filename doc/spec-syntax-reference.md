A spec-map is a data structure used to define specs that are in context for evaluating some expressions.

Specs include variables which have types as:

![type](doc/halite-bnf-diagrams/spec-syntax/type.svg

The variables for a spec are defined in a spec-var-map:

![spec-var-map](doc/halite-bnf-diagrams/spec-syntax/spec-var-map.svg

Constraints on those variables are defined as:

![constraints](doc/halite-bnf-diagrams/spec-syntax/constraints.svg

Any applicable refinements are defined as:

![refinement-map](doc/halite-bnf-diagrams/spec-syntax/refinement-map.svg

All the specs in scope are packaged up into a spec-map:

![spec-map](doc/halite-bnf-diagrams/spec-syntax/spec-map.svg


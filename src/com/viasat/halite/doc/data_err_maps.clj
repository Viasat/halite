;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.data-err-maps)

(set! *warn-on-reflection* true)

(def err-maps
  {'h-err/abs-failure {:doc "The way the number space is divided the value of zero comes out of the positive number space. This means there is one more negative number than there are positive numbers. So there is one negative number whose absolute value cannot be represented. That negative number is the most negative value."}
   'h-err/accumulator-target-must-be-bare-symbol {:doc "In 'reduce', it is necesary to define a symbol to reference the accumulated value of the reduction. This symbol must not include a namespace."
                                                  :err-ref ['h-err/element-binding-target-must-be-bare-symbol]}
   'h-err/arg-type-mismatch {:doc "A relatively generic exception that indicates the operator being invoked cannot operate on the type of value provided."}
   'h-err/not-both-vectors {:doc "When the first argument to 'concat' is a vector, the second must also be a vector. A vector can be concated onto a set, but a set cannot be concated onto a vector."}
   'h-err/argument-empty {:doc "The 'first' operation cannot be invoked on an empty collection."}
   'h-err/argument-not-set-or-vector {:doc "The operation must be invoked a collection."}
   'h-err/argument-not-vector {:doc "The operation can only be invoked on a vector."}
   'h-err/arguments-not-sets {:doc "The operation can only be invoked on set arguments."}
   'h-err/binding-target-must-be-bare-symbol {:doc "In binding forms, the first value of each pair must be a symbol without a namespace. This symbol is an identifier that will be bound to the value of the second item in the pair."}
   'h-err/cannot-bind-reserved-word {:doc "There are a small number of symbols that are reserved for system use and cannot be used by users in bindings."}
   'h-err/cannot-conj-unset {:doc "Only actual values can be added into collections. Specifically 'unset' cannot be added into a collection."}
   'h-err/comprehend-binding-wrong-count {:doc "Collection comprehensions require a single binding that defines the symbol to be bound to the elements of the collection."}
   'h-err/comprehend-collection-invalid-type {:doc "Collection comprehensions can only be applied to collections, i.e. vectors or sets."}
   'h-err/divide-by-zero {:doc "Division by zero, whether directly or indirectly via modulus cannot be performed."}
   'h-err/element-accumulator-same-symbol {:doc "The 'reduce' operation requires distinct symbols for referring to the accumulator and the collection element."}
   'h-err/element-binding-target-must-be-bare-symbol {:doc "In 'reduce', it is necesary to define a symbol without a namepsace which is used to hold each element of the collection."
                                                      :err-ref ['h-err/accumulator-target-must-be-bare-symbol]}
   'h-err/get-in-path-must-be-vector-literal {:doc "The path to navigate in 'get-in' must be a literal, i.e. it cannot be an expression to compute a vector."}
   'h-err/if-value-must-be-bare-symbol {:doc "The 'if-value' operator can only be applied to a bare symbol that is already bound to an optional value."}
   'h-err/index-out-of-bounds {:doc "The index falls outside of the bounds of the vector. A way to avoid this is to first test the length of the vector."}
   'h-err/invalid-exponent {:doc "The exponent cannot be negative."}
   'h-err/invalid-expression {:doc "The expression itself was not recognized as a value that could be evaluated."}
   'h-err/field-value-of-wrong-type {:doc "The value did not match the type of the spec field."}
   'h-err/value-of-wrong-type {:doc "The value did not match the expected type for this symbol in the context."}
   'h-err/invalid-instance {:doc "An attempt was made to create an instance that violated a spec constraint."}
   'h-err/invalid-instance-index {:doc "An attempt was made to a read a value from an instance, but a field name was not provided as an index, instead a value such as an integer was provided."}
   'h-err/invalid-keyword-char {:doc "Only certain characters, in certain sequences are allowed to appear in keywords."}
   'h-err/invalid-keyword-length {:doc "The length of keywords is limited. The supplied keyword exceeded the limit."}
   'h-err/invalid-lookup-target {:doc "An attempt was made to retrieve a field from an instance but the value was not known to be an instance of a specific spec. For example, the value may have been missing, as in the case of an optional field. Or perhaps the instance was a result of an expression and the result could have been an instance of many alternative specs."}
   'h-err/invalid-refinement-expression {:doc "A refinement expression must produce an instance whose :$type matches the type that is declared on the refinement."
                                         :doc-j "A refinement expression must produce an instance whose $type matches the type that is declared on the refinement."}
   'h-err/invalid-refines-to-bound {:doc "Propagate cannot use the given bounds because it refers to a refinement path that doesn't exist."}
   'h-err/invalid-symbol-char {:doc "Only certain characters, in certain sequences are allowed to appear in symbols."}
   'h-err/invalid-symbol-length {:doc "The length of symbols is limited. The supplied symbol exceeded the limit."}
   'h-err/invalid-type-value {:doc "The value of the :$type field in an instance must be a keyword that includes a '/' separator."
                              :doc-j "The value of the $type field in an instance must be a symbol that includes a '/' separator."}
   'h-err/invalid-collection-type {:doc "This indicates that a collection value was provided, but the collection is not of a type that is supported."}
   'h-err/invalid-value {:doc "A value was supplied, but the type of the value is not recognized."}
   'h-err/invalid-vector-index {:doc "An index was supplied to lookup a value in a vector, but the index was not an integer."}
   'h-err/let-bindings-odd-count {:doc "A 'let' expression included an odd number of elements in the binding vector. This is invalid as the bindings are to be a sequence of pairs."}
   'h-err/let-needs-bare-symbol {:doc "In a 'let' expression, the first item in each pair of items must be a symbol."}
   'h-err/limit-exceeded {:doc "There are various, context specific, limits that are enforced. e.g. limits on how deeply expressions may be nested. One of these limits was violated. See the exception data for more details."}
   'h-err/literal-must-evaluate-to-value {:doc "All of the expressions that appear as elements in a collection literal, must be guaranteed to evaluate to values, i.e. they must never evaluate to 'unset'."}
   'h-err/missing-required-vars {:doc "An attempt was made to construct an instance of a spec, without all of its mandatory fields being assigned values."}
   'h-err/missing-type-field {:doc "An attempt was made to construct an instance without providing a value for the :$type field."
                              :doc-j "An attempt was made to construct an instance without providing a value for the $type field."}
   'h-err/must-produce-value {:doc "When using 'map', the expression being evaluated on each element, must produce an actual value, i.e. it must be never produce 'unset'."}
   'h-err/no-abstract {:doc "An attempt was made to construct a concrete instance with a field whose value is an instance of an abstract spec. Any instances used to compose a concrete instance, must themselves be concrete."}
   'h-err/no-refinement-path {:doc "There was no refinement path found to convert a specific instance to a target spec type. There may have been a conditional refinement that did not match the instance, or perhaps there is no refinement path at all."}
   'h-err/no-matching-signature {:doc "An attempt was made to invoke an operation, but either the number of arguments or the types of the arguments was not valid."}
   'h-err/not-boolean-body {:doc "Either an 'any?', 'every?', or 'filter' call was attempted but the expression to evaluate for each element in the collection did not produce a boolean value."}
   'h-err/not-boolean-constraint {:doc "All constraint expressions on specs must produce boolean values. The constraints are predicates which evaluate to true or false to indicate whether the constraint has been met by the instance state."}
   'h-err/not-sortable-body {:doc "When using 'sort-by', the expression used for sorting must produce a value that can be sorted."}
   'h-err/overflow {:doc "The mathematical operation resulted in a number that is too large to fit in the bytes alloted for the numeric type."}
   'h-err/reduce-not-vector {:doc "The 'reduce' operation can only be applied to vectors. Specifically, sets cannot be reduced."}
   'h-err/refinement-error {:doc "An unanticipated error condition was encountered while computing the refinement of an instance."}
   'h-err/resource-spec-not-found {:doc "The spec identifier provided did not correspond to a known spec."}
   'h-err/size-exceeded {:doc "There are various, context specific, limits that are enforced. e.g. limits on the lengths of strings. One of these limits was violated. See the exception data for more details."}
   'h-err/sort-value-collision {:doc "When sorting a collection with 'sort-by', the sort expression must produce a unique value for each element in the collection."}
   'h-err/spec-threw {:doc "This error can occur in two situations. First, it occurs from an explicit invocation of the 'error' operation was encountered in a spec. This indicates that the spec author considers it not possible to proceed in the encountered situation. See the error string in the exception detail for details. Second, this error is produced when a spec if being type checked at runtime."}
   'h-err/instance-threw {:doc "An instance literal produced errors. The specific errors are included in the error data."}
   'h-err/symbol-undefined {:doc "An unbound symbol was referenced in an expression at evaluation time."}
   'h-err/symbols-not-bound {:doc "Unbound symbols are referenced in an expression at type-check time."}
   'h-err/syntax-error {:doc "An object appeared in an expression that is not one of the expected values for the language."}
   'h-err/undefined-symbol {:doc "An unbound symbol was referenced in an expression at type-check time."}
   'h-err/unknown-function-or-operator {:doc "The operator being invoked is not recognized as a valid operation."}
   'h-err/field-name-not-in-spec {:doc "The field name is not valid for the spec. The field name was provided to either define a field value in an instance or to lookup a field in an instance."}
   'h-err/wrong-arg-count {:doc "The number of arguments provided to the operation did not match what was expected."}
   'h-err/wrong-arg-count-min {:doc "The operation expected at least a certain number of arguments. This minimum was not met."}
   'h-err/wrong-arg-count-odd {:doc "The operation expected an odd number of arguments."}
   'h-err/spec-cycle-runtime {:doc "Specs cannot be defined to refine to themselves either directly or transitively. At execution time, this was violated."}
   'h-err/refinement-diamond {:doc "Spec refinements cannot be defined that allow multiple refinement paths between the same two specs."}
   'h-err/spec-cycle {:doc "Dependencies between specs cannot form a cycle."}
   'h-err/spec-map-needed {:doc "This is a low-level exception indicating that an operation was invoked that provided an interface to retreive specs, rather than a literal spec-map."}
   'h-err/unknown-type-collection {:doc "Collections of heterogenous types are not allowed. Similarly collections whose element type cannot be statically determined are not allowed."}
   'l-err/binding-expression-not-optional {:doc "The expression being tested in an 'if-value-let' statement must optionally produce a value."}
   'l-err/binding-target-invalid-symbol {:doc "The symbols to be bound are not to start with a '$'."}
   'l-err/cannot-bind-nothing {:doc "It is not permitted to bind a symbol to 'nothing'."}
   'l-err/cannot-bind-unset {:doc "It is not permitted to rebind the symbol used to represent 'unset'. Instead of defining a symbol for this, consider using '$no-value'."}
   'l-err/disallowed-nothing {:doc "An expression was encountered that does not have a value, but it was used in a place where a value is required. Examples of expressions that do not have values are an invocation of 'error' and the binding of a symbol to an element in an empty collection."}
   'l-err/first-argument-not-optional {:doc "The value being tested in an 'if-value' statement must be optional."}
   'l-err/get-in-path-empty {:doc "A path must be provided to the 'get-in' operation."}
   'l-err/let-bindings-empty {:doc "The bindings form of the 'let' cannot be empty. If there is nothing to bind, then the 'let' can be omitted."}
   'l-err/result-always-known {:doc "The result of the equality check is always the same and can be known in advance, so a check is not needed."}
   'l-err/disallowed-unset-variable {:doc "It is not allowed to bind 'unset' to symbols other than the built-in '$no-value'."}})

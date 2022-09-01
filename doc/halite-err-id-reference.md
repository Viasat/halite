<!---
  This markdown file was generated. Do not edit.
  -->

# Halite err-id reference

### <a name="h-err/abs-failure"></a>h-err/abs-failure

The way the number space is divided the value of zero comes out of the positive number space. This means there is one more negative number than there are positive numbers. So there is one negative number whose absolute value cannot be represented. That negative number is the most negative value.

#### Error message template:

> Cannot compute absolute value of: :value

#### Produced by:

* [`abs`](halite-full-reference.md#abs)

---
### <a name="h-err/accumulator-target-must-be-bare-symbol"></a>h-err/accumulator-target-must-be-bare-symbol

In 'reduce', it is necesary to define a symbol which is used to hold the accumulated value of the reduction. This symbol must not include a namespace.

#### Error message template:

> Accumulator binding target for ':op' must be a bare symbol, not: :accumulator

See also: [`h-err/element-binding-target-must-be-bare-symbol`](#h-err/element-binding-target-must-be-bare-symbol)

---
### <a name="h-err/arg-type-mismatch"></a>h-err/arg-type-mismatch

A relatively generic exception that indicates the operator being invoked cannot operate on the type of value provided.

#### Error message template:

> :position-text to ':op' must be :expected-type-description

#### Produced by:

* [`rescale`](halite-full-reference.md#rescale)

---
### <a name="h-err/argument-empty"></a>h-err/argument-empty

The 'first' operation cannot be invoked on an empty collection.

#### Error message template:

> Argument to first is always empty

#### Produced by:

* [`first`](halite-full-reference.md#first)

---
### <a name="h-err/argument-not-set-or-vector"></a>h-err/argument-not-set-or-vector

The operation must be invoked a collection.

#### Error message template:

> First argument to 'conj' must be a set or vector

---
### <a name="h-err/argument-not-vector"></a>h-err/argument-not-vector

The operation can only be invoked on a vector.

#### Error message template:

> Argument to ':op' must be a vector

---
### <a name="h-err/arguments-not-sets"></a>h-err/arguments-not-sets

The operation can only be invoked on set arguments.

#### Error message template:

> Arguments to ':op' must be sets

---
### <a name="h-err/binding-target-must-be-bare-symbol"></a>h-err/binding-target-must-be-bare-symbol

In binding forms, the first value of each pair must be a symbol without a namespace. This symbol is an identifier that will be bound to the value of the second item in the pair.

#### Error message template:

> Binding target for ':op' must be a bare symbol, not: :sym

---
### <a name="h-err/cannot-bind-reserved-word"></a>h-err/cannot-bind-reserved-word

There are a small number of symbols that are reserved for system use and cannot be used by users in bindings.

#### Error message template:

> Cannot bind a value to the reserved word: :sym

---
### <a name="h-err/cannot-conj-unset"></a>h-err/cannot-conj-unset

Only actual values can be added into collections. Specifically 'unset' cannot be added into a collection.

#### Error message template:

> Cannot conj possibly unset value to :type-string

---
### <a name="h-err/comprehend-binding-wrong-count"></a>h-err/comprehend-binding-wrong-count

Collection comprehensions require a single binding that defines the symbol to be bound to the elements of the collection.

#### Error message template:

> Binding form for 'op' must have one variable and one collection

---
### <a name="h-err/comprehend-collection-invalid-type"></a>h-err/comprehend-collection-invalid-type

Collection comprehensions can only be applies to collections, i.e. vectors or sets.

#### Error message template:

> Collection required for ':op', not :actual-type

---
### <a name="h-err/divide-by-zero"></a>h-err/divide-by-zero

Division by zero, whether directly or indirectly via modulus cannot be performed.

#### Error message template:

> Cannot divide by zero

#### Produced by:

* [`div`](halite-full-reference.md#div)
* [`mod`](halite-full-reference.md#mod)

---
### <a name="h-err/element-accumulator-same-symbol"></a>h-err/element-accumulator-same-symbol

The 'reduce' operation requires distinct symbols for referring to the accumulator and the collection element.

#### Error message template:

> Cannot use the same symbol for accumulator and element binding: :element

---
### <a name="h-err/element-binding-target-must-be-bare-symbol"></a>h-err/element-binding-target-must-be-bare-symbol

In 'reduce', it is necesary to define a symbol without a namepsace which is used to hold each element of the collection.

#### Error message template:

> Element binding target for ':op' must be a bare symbol, not: :element

See also: [`h-err/accumulator-target-must-be-bare-symbol`](#h-err/accumulator-target-must-be-bare-symbol)

---
### <a name="h-err/field-name-not-in-spec"></a>h-err/field-name-not-in-spec

The field name is not valid for the spec. The field name was provided to either define a field value in an instance or to lookup a field in an instance.

#### Error message template:

> Variables not defined on spec: :invalid-vars

#### Produced by:

* [`get`](halite-full-reference.md#get)
* [`get-in`](halite-full-reference.md#get-in)

---
### <a name="h-err/field-value-of-wrong-type"></a>h-err/field-value-of-wrong-type

The value did not match the type of the spec field.

#### Error message template:

> Value of ':variable' has wrong type

---
### <a name="h-err/get-in-path-must-be-vector-literal"></a>h-err/get-in-path-must-be-vector-literal

The path to navigate in 'get-in' must be a literal, i.e. it cannot be an expression to compute a vector.

#### Error message template:

> The path parameter in 'get-in' must be a vector literal: :form

---
### <a name="h-err/if-value-must-be-bare-symbol"></a>h-err/if-value-must-be-bare-symbol

The 'if-value' operator can only be applied to a bare symbol that is already bound to an optional value.

#### Error message template:

> First argument to ':op' must be a bare symbol

---
### <a name="h-err/index-out-of-bounds"></a>h-err/index-out-of-bounds

The index falls outside of the bounds of the vector. A way to avoid this is to first test the length of the vector.

#### Error message template:

> Index out of bounds, :index, for vector of length :length

#### Produced by:

* [`get`](halite-full-reference.md#get)
* [`get-in`](halite-full-reference.md#get-in)

---
### <a name="h-err/invalid-collection-type"></a>h-err/invalid-collection-type

This indicates that a collection value was provided, but the collection is not of a type that is supported.

#### Error message template:

> Collection value is not of a supported type

---
### <a name="h-err/invalid-exponent"></a>h-err/invalid-exponent

The exponent cannot be negative.

#### Error message template:

> Invalid exponent: :exponent

#### Produced by:

* [`expt`](halite-full-reference.md#expt)

---
### <a name="h-err/invalid-expression"></a>h-err/invalid-expression

The expression itself was not recognized as a value that could be evaluated.

#### Error message template:

> Invalid expression

---
### <a name="h-err/invalid-instance"></a>h-err/invalid-instance

An attempt was made to create an instance that violated a spec constraint.

#### Error message template:

> Invalid instance of ':spec-id', violates constraints :violated-constraints

---
### <a name="h-err/invalid-instance-index"></a>h-err/invalid-instance-index

An attempt was made to a read a value from an instance, but a field name was not provided as an index, instead a value such as an integer was provided.

#### Error message template:

> Index must be a variable name (as a keyword) when target is an instance

---
### <a name="h-err/invalid-keyword-char"></a>h-err/invalid-keyword-char

Only certain characters, in certain sequences are allowed to appear in keywords.

#### Error message template:

> The keyword contains invalid characters: :form

---
### <a name="h-err/invalid-keyword-length"></a>h-err/invalid-keyword-length

The length of keywords is limited. The supplied keyword exceeded the limit.

#### Error message template:

> The keyword is too long

---
### <a name="h-err/invalid-lookup-target"></a>h-err/invalid-lookup-target

An attempt was made to retrieve a field from an instance but the value was not known to be an instance of a specific spec. For example, the value may have been missing, as in the case of an optional field. Or perhaps the instance was a result of an expression and the result could have been an instance of many alternative specs.

#### Error message template:

> Lookup target must be an instance of known type or non-empty vector

#### Produced by:

* [`get-in`](halite-full-reference.md#get-in)

---
### <a name="h-err/invalid-refinement-expression"></a>h-err/invalid-refinement-expression

A refinement expression must produce an instance whose :$type matches the type that is declared on the refinement.

#### Error message template:

> Invalid refinement expression: :form

---
### <a name="h-err/invalid-symbol-char"></a>h-err/invalid-symbol-char

Only certain characters, in certain sequences are allowed to appear in symbols.

#### Error message template:

> The symbol contains invalid characters: :form

---
### <a name="h-err/invalid-symbol-length"></a>h-err/invalid-symbol-length

The length of symbols is limited. The supplied symbol exceeded the limit.

#### Error message template:

> The symbol is too long

---
### <a name="h-err/invalid-type-value"></a>h-err/invalid-type-value

The value of the :$type field in an instance must be a keyword that includes a '/' separator.

#### Error message template:

> Expected namespaced keyword as value of :$type

---
### <a name="h-err/invalid-value"></a>h-err/invalid-value

A value was supplied, but the type of the value is not recognized.

#### Error message template:

> Invalid value

---
### <a name="h-err/invalid-vector-index"></a>h-err/invalid-vector-index

An index was supplied to lookup a value in a vector, but the index was not an integer.

#### Error message template:

> Index must be an integer when target is a vector

---
### <a name="h-err/let-bindings-odd-count"></a>h-err/let-bindings-odd-count

A 'let' expression included an odd number of elements in the binding vector. This is invalid as the bindings are to be a sequence of pairs.

#### Error message template:

> Let bindings form must have an even number of forms

---
### <a name="h-err/let-needs-bare-symbol"></a>h-err/let-needs-bare-symbol

In a 'let' expression, the first item in each pair of items must be a symbol.

#### Error message template:

> Even-numbered forms in let binding vector must be bare symbols

---
### <a name="h-err/limit-exceeded"></a>h-err/limit-exceeded

There are various, context specific, limits that are enforced. e.g. limits on how deeply expressions may be nested. One of these limits was violated. See the exception data for more details.

#### Error message template:

> :object-type of :value exceeds the max allowed value of :limit

---
### <a name="h-err/literal-must-evaluate-to-value"></a>h-err/literal-must-evaluate-to-value

All of the expressions that appear as elements in a collection literal, must be guaranteed to evaluate to values, i.e. they must never evaluate to 'unset'.

#### Error message template:

> :coll-type-string literal element must always evaluate to a value

---
### <a name="h-err/missing-required-vars"></a>h-err/missing-required-vars

An attempt was made to construct an instance of a spec, without all of its mandatory fields being assigned values.

#### Error message template:

> Missing required variables: :missing-vars

---
### <a name="h-err/missing-type-field"></a>h-err/missing-type-field

An attempt was made to construct an instance without providing a value for the :$type field.

#### Error message template:

> Instance literal must have :$type field

---
### <a name="h-err/must-produce-value"></a>h-err/must-produce-value

When using 'map', the expression being evaluated on each element, must produce an actual value, i.e. it must be never produce 'unset'.

#### Error message template:

> Expression provided to 'map' must produce a value: :form

---
### <a name="h-err/no-abstract"></a>h-err/no-abstract

An attempt was made to construct a concrete instance with a field whose value is an instance of an abstract spec. Any instances used to compose a concrete instance, must themselves be concrete.

#### Error message template:

> Instance cannot contain abstract value

---
### <a name="h-err/no-matching-signature"></a>h-err/no-matching-signature

An attempt was made to invoke an operation, but either the number of arguments or the types of the arguments was not valid.

#### Error message template:

> No matching signature for ':op'

---
### <a name="h-err/no-refinement-path"></a>h-err/no-refinement-path

There was no refinement path found to convert a specific instance to a target spec type. There may have been a conditional refinement that did not match the instance, or perhaps there is no refinement path at all.

#### Error message template:

> No active refinement path from ':type' to ':target-type'

#### Produced by:

* [`refine-to`](halite-full-reference.md#refine-to)

---
### <a name="h-err/not-boolean-body"></a>h-err/not-boolean-body

Either an 'any?', 'every?', or 'filter' call was attempted but the expression to evaluate for each element in the collection did not produce a boolean value.

#### Error message template:

> Body expression in ':op' must be boolean

---
### <a name="h-err/not-boolean-constraint"></a>h-err/not-boolean-constraint

All constraint expressions on specs must produce boolean values. The constraints are predicates which evaluate to true or false to indicate whether the constraint has been met by the instance state.

#### Error message template:

> Constraint expression ':expr' must have Boolean type

---
### <a name="h-err/not-both-vectors"></a>h-err/not-both-vectors

When the first argument to 'concat' is a vector, the second must also be a vector. A vector can be concated onto a set, but a set cannot be concated onto a vector.

#### Error message template:

> When first argument to ':op' is a vector, second argument must also be a vector

---
### <a name="h-err/not-sortable-body"></a>h-err/not-sortable-body

When using 'sort-by', the expression used for sorting must produce a value that can be sorted.

#### Error message template:

> Body expression in ':op' must be sortable, not :actual-type

#### Produced by:

* [`sort-by`](halite-full-reference.md#sort-by)

---
### <a name="h-err/overflow"></a>h-err/overflow

The mathematical operation resulted in a number that is too large to fit in the bytes alloted for the numeric type.

#### Error message template:

> Numeric value overflow

#### Produced by:

* [`*`](halite-full-reference.md#_S)
* [`+`](halite-full-reference.md#_A)
* [`-`](halite-full-reference.md#-)
* [`dec`](halite-full-reference.md#dec)
* [`expt`](halite-full-reference.md#expt)
* [`inc`](halite-full-reference.md#inc)

---
### <a name="h-err/reduce-not-vector"></a>h-err/reduce-not-vector

The 'reduce' can only be applied to vectors. Specifically, sets cannot be reduced.

#### Error message template:

> Second binding expression to 'reduce' must be a vector.

---
### <a name="h-err/refinement-error"></a>h-err/refinement-error

An unanticipated error condition was encountered while computing the refinement of an instance.

#### Error message template:

> Refinement from ':type' failed unexpectedly: :underlying-error-message

---
### <a name="h-err/resource-spec-not-found"></a>h-err/resource-spec-not-found

The spec identifier provided did not correspond to a known spec.

#### Error message template:

> Resource spec not found: :spec-id

#### Produced by:

* [`refine-to`](halite-full-reference.md#refine-to)
* [`refines-to?`](halite-full-reference.md#refines-to_Q)

---
### <a name="h-err/size-exceeded"></a>h-err/size-exceeded

There are various, context specific, limits that are enforced. e.g. limits on the lengths of strings. One of these limits was violated. See the exception data for more details.

#### Error message template:

> :object-type size of :actual-count exceeds the max allowed size of :count-limit

See also: [`h-err/count-exceeded`](#h-err/count-exceeded)

---
### <a name="h-err/sort-value-collision"></a>h-err/sort-value-collision

When sorting a collection with 'sort-by', the sort expression must produce a unique value for each element in the collection.

#### Error message template:

> Multiple elements produced the same sort value, so the collection cannot be deterministically sorted

#### Produced by:

* [`sort-by`](halite-full-reference.md#sort-by)

---
### <a name="h-err/spec-threw"></a>h-err/spec-threw

An explicit invocation of the 'error' operation was encountered in a spec. This indicates that the spec author considers this instance to be invalid. See the error string in the exception detail for details.

#### Error message template:

> Spec threw error: :spec-error-str

#### Produced by:

* [`error`](halite-full-reference.md#error)

---
### <a name="h-err/symbol-undefined"></a>h-err/symbol-undefined

An unbound symbol was referenced in an expression at evaluation time.

#### Error message template:

> Symbol ':form' is undefined

---
### <a name="h-err/symbols-not-bound"></a>h-err/symbols-not-bound

Unbound symbols are referenced in an expression at type-check time.

#### Error message template:

> Symbols in type environment are not bound: :unbound-symbols

---
### <a name="h-err/syntax-error"></a>h-err/syntax-error

An object appeared in an expression that is not one of the expected values for the language.

#### Error message template:

> Syntax error

---
### <a name="h-err/undefined-symbol"></a>h-err/undefined-symbol

An unbound symbol was referenced in an expression at type-check time.

#### Error message template:

> Undefined: ':form'

---
### <a name="h-err/unknown-function-or-operator"></a>h-err/unknown-function-or-operator

The operator being invoked is not recognized as a valid operation.

#### Error message template:

> Unknown function or operator: :op

---
### <a name="h-err/value-of-wrong-type"></a>h-err/value-of-wrong-type

The value did not match the expected type for this symbol in the context.

#### Error message template:

> Value of ':sym has wrong type

---
### <a name="h-err/wrong-arg-count"></a>h-err/wrong-arg-count

The number of arguments provided to the operation did not match what was expected.

#### Error message template:

> Wrong number of arguments to ':op': expected :expected-arg-count, but got :actual-arg-count

---
### <a name="h-err/wrong-arg-count-min"></a>h-err/wrong-arg-count-min

The operation expected at least a certain number of arguments. This minimum was not met.

#### Error message template:

> Wrong number of arguments to ':op': expected at least :minimum-arg-count, but got :actual-arg-count

---
### <a name="l-err/binding-expression-not-optional"></a>l-err/binding-expression-not-optional

The expression being tested in an 'if-value-let' statement must optionally produce a value.

#### Error message template:

> Binding expression in ':op' must have an optional type

---
### <a name="l-err/binding-target-invalid-symbol"></a>l-err/binding-target-invalid-symbol

The symbols to be bound are not to start with a '$'.

#### Error message template:

> Binding target for ':op' must not start with '$': :sym

---
### <a name="l-err/cannot-bind-nothing"></a>l-err/cannot-bind-nothing

It is not permitted to rebind the symbol used to represent 'unset'.

#### Error message template:

> Disallowed binding ':sym' to \colonNothing value; perhaps move to body of 'let'

---
### <a name="l-err/cannot-bind-unset"></a>l-err/cannot-bind-unset

It is not permitted to rebind the symbol used to represent 'unset'.

#### Error message template:

> Disallowed binding ':sym' to \colonUnset value; just use '$no-value'

---
### <a name="l-err/disallowed-nothing"></a>l-err/disallowed-nothing

An expression was encountered that does not have a value, but it was used in a place where a value is required. Examples of expressions that do not have values are an invocation of 'error' and the binding of a symbol to an element in an empty collection.

#### Error message template:

> Disallowed '\colonNothing' expression: :nothing-arg

---
### <a name="l-err/disallowed-unset-variable"></a>l-err/disallowed-unset-variable

It is not allowed to bind 'unset' to symbols other than the built-in '$no-value'.

#### Error message template:

> Disallowed use of Unset variable ':form'; you may want '$no-value'

---
### <a name="l-err/first-argument-not-optional"></a>l-err/first-argument-not-optional

The value being tested in an 'if-value' statement must be optional.

#### Error message template:

> First argument to ':op' must have an optional type

---
### <a name="l-err/get-in-path-empty"></a>l-err/get-in-path-empty

A path must be provided to the 'get-in' operation.

#### Error message template:

> The path parameter in 'get-in' cannot be empty: :form

#### Produced by:

* [`get-in`](halite-full-reference.md#get-in)

---
### <a name="l-err/let-bindings-empty"></a>l-err/let-bindings-empty

The bindings form of the 'let' cannot be empty. If there is nothing to bind, then the 'let' can be omitted.

#### Error message template:

> Bindings form of 'let' cannot be empty in: :form

---
### <a name="l-err/result-always-known"></a>l-err/result-always-known

The result of the equality check is always the same and can be known in advance, so it is not needed.

#### Error message template:

> Result of ':op' would always be :value

---
### <a name="s-err/all-refinements-must-have-refinement-e-value"></a>s-err/all-refinements-must-have-refinement-e-value



#### Error message template:

> Expected all refinements to have a refinement-e value

---
### <a name="s-err/bom-expression-invalid"></a>s-err/bom-expression-invalid



#### Error message template:

> Invalid right :right, it must be one of [admin, read, write, delete, purge, register, registry-read, registry-write, registry-publish, registry-delete]

---
### <a name="s-err/cannot-perform-registry-op-with-old-time"></a>s-err/cannot-perform-registry-op-with-old-time



#### Error message template:

> Cannot perform registry op on workspace: :workspace-name with a time value of: :instruction-time which is not after the last instruction time on this workspace of: :last-modified-time

---
### <a name="s-err/cannot-remove-published-spec"></a>s-err/cannot-remove-published-spec



#### Error message template:

> Cannot :op spec that is still published to outside registry: :just-keys

---
### <a name="s-err/cannot-remove-workspace-with-registered-specs"></a>s-err/cannot-remove-workspace-with-registered-specs



#### Error message template:

> Cannot :op workspace that contains specs registered in other workspaces: :just-keys

---
### <a name="s-err/coercer-error"></a>s-err/coercer-error



#### Error message template:

> Didn't find coercer in cache: :schema-name

---
### <a name="s-err/constraints-must-be-booleans"></a>s-err/constraints-must-be-booleans



#### Error message template:

> Constraint :constraint-name on spec :spec-id must have type Boolean, not :actual-type

---
### <a name="s-err/expressions-disallowed-language"></a>s-err/expressions-disallowed-language



#### Error message template:

> Must use halite/jadeite for all expressions

---
### <a name="s-err/group-category-invalid-characters"></a>s-err/group-category-invalid-characters



#### Error message template:

> strInvalid characters in group-category :group-category, they must match the regex 'model/group-category-pattern'

---
### <a name="s-err/group-category-invalid-length"></a>s-err/group-category-invalid-length



#### Error message template:

> strInvalid group-category length :count, it must be <= model/group-category-length-limit

---
### <a name="s-err/group-name-invalid-characters"></a>s-err/group-name-invalid-characters



#### Error message template:

> strInvalid characters in group-name :group-name, they must match the regex 'model/group-name-pattern'

---
### <a name="s-err/group-name-invalid-length"></a>s-err/group-name-invalid-length



#### Error message template:

> strInvalid group-name length :count, it must be <= model/group-name-length-limit

---
### <a name="s-err/instructions-cannot-reference-same-spec-twice"></a>s-err/instructions-cannot-reference-same-spec-twice



#### Error message template:

> Instructions cannot refer to the same spec twice in a single workspace: :entry-spec-id

---
### <a name="s-err/json-coercion-failure"></a>s-err/json-coercion-failure



#### Error message template:

> Coercion failure at :path : :err value: :orig

---
### <a name="s-err/json-key-error"></a>s-err/json-key-error



#### Error message template:

> Unsuported key :schema-error found in json body

---
### <a name="s-err/json-missing-key-error"></a>s-err/json-missing-key-error



#### Error message template:

> Required key :missing-field not found in json body

---
### <a name="s-err/json-type-invalid"></a>s-err/json-type-invalid



#### Error message template:

> VariableType :type in JSON must be string or object

---
### <a name="s-err/last-transaction-id-requires-su"></a>s-err/last-transaction-id-requires-su



#### Error message template:

> Cannot provide last-transaction-id without \colonsu right

---
### <a name="s-err/multiple-paths-not-allowed"></a>s-err/multiple-paths-not-allowed



#### Error message template:

> Multiple paths detected: :spec-id :reachable

---
### <a name="s-err/must-specify-spec-is-concrete-value"></a>s-err/must-specify-spec-is-concrete-value



#### Error message template:

> Must specify whether spec is concrete

---
### <a name="s-err/op-type-combine-error"></a>s-err/op-type-combine-error



#### Error message template:

> cannot combine types: [:types]

---
### <a name="s-err/permission-not-found"></a>s-err/permission-not-found



#### Error message template:

> Permission not found: :permission

---
### <a name="s-err/refinement-has-wrong-type"></a>s-err/refinement-has-wrong-type



#### Error message template:

> Refinement to :expected-type has wrong type ':actual-type'

---
### <a name="s-err/refinements-disallowed-language"></a>s-err/refinements-disallowed-language



#### Error message template:

> Expected all refinements to use halite or jadeite

---
### <a name="s-err/refinements-duplicated-spec-id"></a>s-err/refinements-duplicated-spec-id



#### Error message template:

> Did not expect multiple refinements for same spec id

---
### <a name="s-err/registry-entry-must-not-provide-is-concrete-value"></a>s-err/registry-entry-must-not-provide-is-concrete-value



#### Error message template:

> Registry entry must not provide 'is-concrete' value: :instruction

---
### <a name="s-err/registry-entry-must-provide-is-concrete-value"></a>s-err/registry-entry-must-provide-is-concrete-value



#### Error message template:

> Registry entry must provide 'is-concrete' value: :instruction

---
### <a name="s-err/registry-invalid-op-error"></a>s-err/registry-invalid-op-error



#### Error message template:

> Invalid registry-op :registry-op, it must be one of [register, publish, or delete]

---
### <a name="s-err/registry-spec-id-error"></a>s-err/registry-spec-id-error



#### Error message template:

> spec-id :spec-id not registered in the workspace

---
### <a name="s-err/resource-spec-id-not-found"></a>s-err/resource-spec-id-not-found



#### Error message template:

> Resource Spec :spec-id not found, or access not granted

---
### <a name="s-err/resource-spec-name-not-found"></a>s-err/resource-spec-name-not-found



#### Error message template:

> Resource Spec :resource-spec-name not found, or access not granted

---
### <a name="s-err/resource-spec-not-found"></a>s-err/resource-spec-not-found



#### Error message template:

> Resource spec not found: :spec-id

---
### <a name="s-err/resource-spec-query-version-not-found"></a>s-err/resource-spec-query-version-not-found



#### Error message template:

> Query parameter version :version not found

---
### <a name="s-err/resource-spec-reference-not-found"></a>s-err/resource-spec-reference-not-found



#### Error message template:

> Referenced resource spec :spec-id not found, or access not granted

---
### <a name="s-err/resource-spec-variable-map-count-error"></a>s-err/resource-spec-variable-map-count-error



#### Error message template:

> variable-type collections must contain only a collection-type and element-type definition

---
### <a name="s-err/resource-spec-version-0-create-error"></a>s-err/resource-spec-version-0-create-error



#### Error message template:

> Resource Spec does not exist, cannot create when version != 0

---
### <a name="s-err/resource-spec-version-0-update-error"></a>s-err/resource-spec-version-0-update-error



#### Error message template:

> Resource Spec already exists, cannot perform update when version == 0

---
### <a name="s-err/resource-spec-version-error"></a>s-err/resource-spec-version-error



#### Error message template:

> strInvalid resource-spec-version :resource-spec-version, it must match the regex model/spec-version-url-pattern or 'latest'

---
### <a name="s-err/resource-spec-version-invalid-error"></a>s-err/resource-spec-version-invalid-error



#### Error message template:

> strInvalid resource-spec-version :resource-spec-version, it must match the regex model/spec-version-url-pattern or 'latest'

---
### <a name="s-err/right-invalid"></a>s-err/right-invalid



#### Error message template:

> Invalid right :right, it must be one of [admin, read, write, delete, purge, register, registry-read, registry-write, registry-publish, registry-delete]

---
### <a name="s-err/source-registry-not-found"></a>s-err/source-registry-not-found



#### Error message template:

> Source registry entry not found: :instruction

---
### <a name="s-err/spec-does-not-exist"></a>s-err/spec-does-not-exist



#### Error message template:

> Resource spec :spec-id does not exist

---
### <a name="s-err/spec-id-invalid-characters"></a>s-err/spec-id-invalid-characters



#### Error message template:

> strInvalid characters in spec-id :spec-id, they must match the regex 'model/dotted-name/model/user-specid-regex-pattern'

---
### <a name="s-err/spec-id-length-error"></a>s-err/spec-id-length-error



#### Error message template:

> strInvalid spec-id length :count, it must be <= model/user-spec-id-length-limit

---
### <a name="s-err/spec-id-query-error"></a>s-err/spec-id-query-error



#### Error message template:

> spec-id query parameter must be specified

---
### <a name="s-err/spec-name-invalid-characters"></a>s-err/spec-name-invalid-characters



#### Error message template:

> strInvalid characters in spec-name :spec-name, they must match the regex 'model/dotted-name/model/spec-name-regex-pattern'

---
### <a name="s-err/spec-name-invalid-length"></a>s-err/spec-name-invalid-length



#### Error message template:

> strInvalid spec-name length :count, it must be <= model/spec-name-length-limit

---
### <a name="s-err/spec-not-in-registry"></a>s-err/spec-not-in-registry



#### Error message template:

> Spec referenced not in registry: :spec-id

---
### <a name="s-err/too-many-registry-instructions"></a>s-err/too-many-registry-instructions



#### Error message template:

> Too many registry instructions (more than :limit): :count

---
### <a name="s-err/unexpected-result"></a>s-err/unexpected-result



#### Error message template:

> Unexpected result, please contact support

---
### <a name="s-err/unknown-error"></a>s-err/unknown-error



#### Error message template:

> Exception processing request

---
### <a name="s-err/variable-does-not-exist"></a>s-err/variable-does-not-exist



#### Error message template:

> Variable :variable not defined in resource spec

---
### <a name="s-err/workspace-name-invalid-characters"></a>s-err/workspace-name-invalid-characters



#### Error message template:

> strInvalid characters in workspace-name :workspace-name, they must match the regex 'model/dotted-name'

---
### <a name="s-err/workspace-name-invalid-length"></a>s-err/workspace-name-invalid-length



#### Error message template:

> strInvalid workspace-name length :count, it must be <= model/workspace-name-length-limit

---
### <a name="s-err/workspace-not-found"></a>s-err/workspace-not-found



#### Error message template:

> Workspace :workspace-name not found, or access not granted

---

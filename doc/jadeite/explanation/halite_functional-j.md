<!---
  This markdown file was generated. Do not edit.
  -->

## Language is functional

Both Halite, and its alternate representation, Jadeite, are purely functional languages.

Halite is a functional language in the following ways:

* The result of all operations are purely the result of the input values provided to the operator.

* The only result of operations is their return value, i.e. there are no side-effects*.

* Every operation is an expression that produces a value, i.e. there are no statements.

* All of the values are immutable.

* Every operation is deterministic, i.e. the value produced by an expression is the same every time the expression is evaluated. There are no random numbers, there is no I/O, there is no mutable state. For example, this is why there is no operation to 'reduce' a set, because the order of the reduction would not be well-defined given the language semantics.

From some perspectives, Halite is not a functional language. For example:

* There is one category of side-effect, expressions can produce errors. This is seen in the mathematical operators which can overflow or produce a 'divide by zero' error. Similarly, attempting to access an element beyond the bounds of a vector, attempting to construct an invalid instance, attempting an impossible refinement all produce errors at runtime, and the 'error' operator which explicitly raises a runtime error. These runtime errors are part of the semantics of the language. Typically, these errors are guarded against by use of an 'if' statement which conditionally evaluates an expression which might produce an error. These runtime errors are part of the language, unlike underlying system errors such as running out of memory, devices failing, or bugs in the stack of code that is evaluating an expression. Such system errors are not part of the language, but of course can occur at any time.

* It does not allow functions to be used as values.

* It does not support higher-ordered functions, i.e. functions are not assigned to variables, passed to operators or returned from operators. Instead the language relies on comprehensions for processing sequences of values.

* It does not allow for user-defined functions. Of course, specs themselves are a kind of 'user-defined function'.

* Considered from the perspective of errors as side-effects, the 'let' binding expressions can be viewed as statements which have the effect of producing errors.


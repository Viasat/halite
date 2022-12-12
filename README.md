# Halite

A tool for specification and verification of systems.

Halite consists of

1. A specification language for defining schemas and constraints
2. An evaluator that can be used to verify instances or compositions of schemas and constraints.
3. A generator that can create possible solutions.

Halite is the mineral form of salt (sodium chloride) commonly known as "rock salt".
This tool is named Halite as the successor to [Salt](https://github.com/Viasat/salt)
(S-expressions for Actions with Logic Temporal).
Salt provided a Clojure interface to TLA+.
Halite provides Edn and Json interfaces for specifying schemas
and constraints. Constraints are solved by [Choco-solver](https://choco-solver.org/).

## Rationale

Systems are composed of services.
Service components are often maintained by different teams.
The interfaces and behaviors of the system need to be clear, precise, and verifiable in order to create a cohesive system.
Halite provides a formal way to define interfaces and behaviors.
Using this approach to model and reason about a system enables proof that the design is logical, and that the outcomes are known.

We see potential use cases to include

1. **Proving systems.** As a standalone proof system to ensure that the designed composition of services is logical, and that specific instances of communication are valid.
2. **Constructing systems.** As an integrated shell for the system that automatically validates boundary communication.
3. **Applying constraints in programs.** Integrated into services to validate inputs and outputs of processes.

## Usage

This is a pre-release version and the API is unstable.
No releases are available yet, instead clone this repo or use as a git dependency.

## Documentation

Coming soon: [Outline](doc/halite_outline.md)

Meanwhile:

* [Fields](doc/halite/explanation/halite_abstract-field.md)
* [Specifications](doc/halite/explanation/halite_abstract-spec.md)


## Contributing

Pull requests and issues are welcome.

### Setup

Install Leinigen and Java.

### Testing

```
lein test
```

<!---
  This markdown file was generated. Do not edit.
  -->

## Model a notebook mechanism

Model a notebook mechanism

The following is an extended example of implementing a non-trivial amount of logic in a set of specs. It is a bit "meta", but in this case the model will include specs that exist in workspaces where each spec has a version separate from its name. 

Versions must be positive values.

```java
{
  "tutorials.notebook/Version$v1" : {
    "fields" : {
      "version" : [ "Maybe", "Integer" ]
    },
    "constraints" : [ "{expr: (ifValue(version) {(version > 0)} else {true}), name: \"positiveVersion\"}" ]
  }
}
```

An identifier for a spec includes its name and version. By referencing the Version spec in a constraint we can reuse the constraint from the Version spec.

```java
{
  "tutorials.notebook/SpecId$v1" : {
    "fields" : {
      "workspaceName" : "String",
      "specName" : "String",
      "specVersion" : "Integer"
    },
    "constraints" : [ "{expr: (valid? {$type: tutorials.notebook/Version$v1, version: specVersion}), name: \"positiveVersion\"}" ]
  }
}
```

A notebook will consist of "items". Since there are different kinds of items an abstract spec is defined.

```java
{
  "tutorials.notebook/AbstractNotebookItem$v1" : {
    "abstract?" : true
  }
}
```

One kind of item is a reference to another spec.

```java
{
  "tutorials.notebook/SpecRef$v1" : {
    "fields" : {
      "workspaceName" : "String",
      "specName" : "String",
      "specVersion" : [ "Maybe", "Integer" ]
    },
    "constraints" : [ "{expr: (valid? {$type: tutorials.notebook/Version$v1, version: specVersion}), name: \"positiveVersion\"}" ],
    "refines-to" : {
      "tutorials.notebook/SpecId$v1" : {
        "name" : "specId",
        "expr" : "(whenValue(specVersion) {{$type: tutorials.notebook/SpecId$v1, specName: specName, specVersion: specVersion, workspaceName: workspaceName}})"
      },
      "tutorials.notebook/AbstractNotebookItem$v1" : {
        "name" : "abstractItems",
        "expr" : "{$type: tutorials.notebook/AbstractNotebookItem$v1}"
      }
    }
  }
}
```

Example of a "fixed" spec references that refers precisely to a given version of a spec.

```java
{$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 1, workspaceName: "my"}
```

Example of a "floating" spec reference that refers to the latest version of a given spec within whatever context the reference is resolved in.

```java
{$type: tutorials.notebook/SpecRef$v1, specName: "A", workspaceName: "my"}
```

Another kind of notebook item is the definition of a new spec. Notice in this case the optional flag only presents two options: either it is present and set to 'true' or it is absent. So it is truly a binary. The convenience this approach provides is that it is less verbose to have it excluded it from instances where it is not set.

```java
{
  "tutorials.notebook/NewSpec$v1" : {
    "fields" : {
      "workspaceName" : "String",
      "specName" : "String",
      "specVersion" : "Integer",
      "isEphemeral" : [ "Maybe", "Boolean" ]
    },
    "constraints" : [ "{expr: (ifValue(isEphemeral) {isEphemeral} else {true}), name: \"ephemeralFlag\"}", "{expr: (valid? {$type: tutorials.notebook/Version$v1, version: specVersion}), name: \"positiveVersion\"}" ],
    "refines-to" : {
      "tutorials.notebook/SpecId$v1" : {
        "name" : "specId",
        "expr" : "{$type: tutorials.notebook/SpecId$v1, specName: specName, specVersion: specVersion, workspaceName: workspaceName}"
      },
      "tutorials.notebook/AbstractNotebookItem$v1" : {
        "name" : "abstractItems",
        "expr" : "{$type: tutorials.notebook/AbstractNotebookItem$v1}"
      }
    }
  }
}
```

Some examples of 'new specs'.

```java
{$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 1, workspaceName: "my"}
```

```java
{$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 0, workspaceName: "my"}


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""]
```

```java
{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "A", specVersion: 1, workspaceName: "my"}
```

It is not possible to set the flag to a value of 'false', instead it is to be omitted.

```java
{$type: tutorials.notebook/NewSpec$v1, isEphemeral: false, specName: "A", specVersion: 1, workspaceName: "my"}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/NewSpec$v1', violates constraints \"tutorials.notebook/NewSpec$v1/ephemeralFlag\""]
```

A 'new spec' can be treated as a spec identifier.

```java
{$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 1, workspaceName: "my"}.refineTo( tutorials.notebook/SpecId$v1 )


//-- result --
{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}
```

This is an example of writing a reusable 'function' via a spec. In this case the prefix 'F' is used in the spec name to identify it as following this pattern. The fields in this spec are the input parameters to the function. The spec representing the return value from the function is prefixed with an 'R'. Since an integer value cannot be produced from a refinement, a spec is needed to hold the result.

```java
{
  "tutorials.notebook/RInteger$v1" : {
    "fields" : {
      "result" : [ "Maybe", "Integer" ]
    }
  },
  "tutorials.notebook/FMaxSpecVersion$v1" : {
    "fields" : {
      "specIds" : [ "Vec", "tutorials.notebook/SpecId$v1" ],
      "workspaceName" : "String",
      "specName" : "String"
    },
    "refines-to" : {
      "tutorials.notebook/RInteger$v1" : {
        "name" : "result",
        "expr" : "{$type: tutorials.notebook/RInteger$v1, result: ({ result = (reduce( a = 0; si in specIds ) { (if(((si.workspaceName != workspaceName) || (si.specName != specName))) {a} else {(if((si.specVersion > a)) {si.specVersion} else {a})}) }); (when((0 != result)) {result}) })}"
      }
    }
  }
}
```

Some examples of invoking the function to find the max version of a given spec.

```java
{$type: tutorials.notebook/FMaxSpecVersion$v1, specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], specName: "A", workspaceName: "my"}.refineTo( tutorials.notebook/RInteger$v1 )


//-- result --
{$type: tutorials.notebook/RInteger$v1, result: 2}
```

```java
{$type: tutorials.notebook/FMaxSpecVersion$v1, specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], specName: "B", workspaceName: "my"}.refineTo( tutorials.notebook/RInteger$v1 )


//-- result --
{$type: tutorials.notebook/RInteger$v1, result: 1}
```

An example of when there is no such spec in the set of specs.

```java
{$type: tutorials.notebook/FMaxSpecVersion$v1, specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], specName: "C", workspaceName: "my"}.refineTo( tutorials.notebook/RInteger$v1 )


//-- result --
{$type: tutorials.notebook/RInteger$v1}
```

A spec which resolves a spec reference in the context of other specs. In this case, this is like a function, but there is a natural way to express the result using an existing spec.

```java
{
  "tutorials.notebook/SpecRefResolver$v1" : {
    "fields" : {
      "existingSpecIds" : [ "Vec", "tutorials.notebook/SpecId$v1" ],
      "newSpecs" : [ "Vec", "tutorials.notebook/NewSpec$v1" ],
      "inputSpecRef" : "tutorials.notebook/SpecRef$v1"
    },
    "refines-to" : {
      "tutorials.notebook/SpecId$v1" : {
        "name" : "toSpecId",
        "expr" : "({ 'all-spec-ids' = existingSpecIds.concat((map(ns in newSpecs)ns.refineTo( tutorials.notebook/SpecId$v1 ))); 'spec-version' = inputSpecRef.specVersion; (ifValue('spec-version') {({ result = inputSpecRef.refineTo( tutorials.notebook/SpecId$v1 ); (when(#{}.concat('all-spec-ids').contains?(result)) {result}) })} else {({ 'max-version-in-context' = {$type: tutorials.notebook/FMaxSpecVersion$v1, specIds: 'all-spec-ids', specName: inputSpecRef.specName, workspaceName: inputSpecRef.workspaceName}.refineTo( tutorials.notebook/RInteger$v1 ).result; (whenValue('max-version-in-context') {{$type: tutorials.notebook/SpecId$v1, specName: inputSpecRef.specName, specVersion: 'max-version-in-context', workspaceName: inputSpecRef.workspaceName}}) })}) })"
      }
    }
  }
}
```

Cases where the input spec reference cannot be resolved are represented by failing to refine.

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 1, workspaceName: "my"}, newSpecs: []}.refinesTo?( tutorials.notebook/SpecId$v1 )


//-- result --
false
```

For cases that can be refined, the refinement result is the result of resolving the spec reference. In this example the floating reference resolves to a new spec.

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "A", workspaceName: "my"}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "C", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 3, workspaceName: "my"}]}.refineTo( tutorials.notebook/SpecId$v1 )


//-- result --
{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 3, workspaceName: "my"}
```

In this example a floating spec reference resolves to an existing spec in the current context.

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "B", workspaceName: "my"}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "C", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 3, workspaceName: "my"}]}.refineTo( tutorials.notebook/SpecId$v1 )


//-- result --
{$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}
```

An example of resolving a fixed spec reference.

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "B", specVersion: 1, workspaceName: "my"}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "C", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 3, workspaceName: "my"}]}.refineTo( tutorials.notebook/SpecId$v1 )


//-- result --
{$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}
```

An example of resolving a reference to an ephemeral spec.

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "C", specVersion: 1, workspaceName: "my"}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "C", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 3, workspaceName: "my"}]}.refineTo( tutorials.notebook/SpecId$v1 )


//-- result --
{$type: tutorials.notebook/SpecId$v1, specName: "C", specVersion: 1, workspaceName: "my"}
```

A reference to a hypothetical ephemeral spec that does not exist does not resolve.

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "C", specVersion: 2, workspaceName: "my"}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "C", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 3, workspaceName: "my"}]}.refinesTo?( tutorials.notebook/SpecId$v1 )


//-- result --
false
```

A reference to a completely unknown spec name does not resolve.

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "X", specVersion: 1, workspaceName: "my"}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "C", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 3, workspaceName: "my"}]}.refinesTo?( tutorials.notebook/SpecId$v1 )


//-- result --
false
```

Make a spec to hold the result of resolving all spec references in a notebook.

```java
{
  "tutorials.notebook/ResolvedSpecRefs$v1" : {
    "fields" : {
      "specRefs" : [ "Vec", "tutorials.notebook/SpecId$v1" ]
    }
  }
}
```

A notebook contains spec references. This is modeled as the results of having parsed the references out of the contents of the notebook. Constraints are added to make some of the vector fields have set semantics (i.e. to not allow duplicates). This is done rather than representing the fields as sets because sequence operations cannot be deterministically applied to sets.

```java
{
  "tutorials.notebook/Notebook$v1" : {
    "fields" : {
      "name" : "String",
      "version" : "Integer",
      "items" : [ "Vec", "tutorials.notebook/AbstractNotebookItem$v1" ]
    },
    "constraints" : [ "{expr: (valid? {$type: tutorials.notebook/Version$v1, version: version}), name: \"positiveVersion\"}" ]
  }
}
```

The contents of notebooks can be used as the basis for defining regression tests for a workspace.

```java
{
  "tutorials.notebook/RegressionTest$v1" : {
    "fields" : {
      "notebookName" : "String",
      "notebookVersion" : "Integer"
    },
    "constraints" : [ "{expr: (valid? {$type: tutorials.notebook/Version$v1, version: notebookVersion}), name: \"positiveVersion\"}" ]
  }
}
```

Finally, we can create a top-level spec that represents a workspace and the items it contains. Two separate fields are used to represent the specs that are available in a workspace. One captures all of those that are registered. The other captures, just the "private" specs that are defined in this workspace, but not made available in the registry.

```java
{
  "tutorials.notebook/Workspace$v1" : {
    "fields" : {
      "workspaceName" : "String",
      "registrySpecIds" : [ "Vec", "tutorials.notebook/SpecId$v1" ],
      "specIds" : [ "Vec", "tutorials.notebook/SpecId$v1" ],
      "notebooks" : [ "Vec", "tutorials.notebook/Notebook$v1" ],
      "tests" : [ "Vec", "tutorials.notebook/RegressionTest$v1" ]
    },
    "constraints" : [ "{expr: (0 == #{}.concat(registrySpecIds).intersection(#{}.concat(specIds)).count()), name: \"specIdsDisjoint\"}", "{expr: (#{}.concat((map(n in notebooks)n.name)).count() == notebooks.count()), name: \"uniqueNotebookNames\"}", "{expr: (#{}.concat((map(t in tests)t.notebookName)).count() == tests.count()), name: \"uniqueTestNames\"}", "{expr: (0 == (filter(si in specIds)(si.workspaceName != workspaceName)).count()), name: \"privateSpecIdsInThisWorkspace\"}", "{expr: (#{}.concat(specIds).count() == specIds.count()), name: \"uniqueSpecIds\"}", "{expr: (#{}.concat(registrySpecIds).count() == registrySpecIds.count()), name: \"uniqueRegistrySpecIds\"}" ]
  }
}
```

An example of a valid workspace instance.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 1}], registrySpecIds: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], tests: [], workspaceName: "my"}
```

Example workspace instance that violates a spec id constraint.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 1}], registrySpecIds: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], tests: [], workspaceName: "my"}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/Workspace$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueSpecIds\""]
```

Example of a workspace that violates a notebook name constraint.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 1}, {$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 2}], registrySpecIds: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], tests: [], workspaceName: "my"}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/Workspace$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueNotebookNames\""]
```

```java
{
  "tutorials.notebook/ResolveRefsState$v1" : {
    "fields" : {
      "context" : [ "Vec", "tutorials.notebook/SpecId$v1" ],
      "resolved" : [ "Vec", "tutorials.notebook/SpecId$v1" ]
    }
  },
  "tutorials.notebook/RSpecIds$v1" : {
    "fields" : {
      "result" : [ "Vec", "tutorials.notebook/SpecId$v1" ]
    }
  },
  "tutorials.notebook/ResolveRefsDirect$v1" : {
    "fields" : {
      "specIds" : [ "Vec", "tutorials.notebook/SpecId$v1" ],
      "items" : [ "Vec", "tutorials.notebook/AbstractNotebookItem$v1" ]
    },
    "refines-to" : {
      "tutorials.notebook/RSpecIds$v1" : {
        "name" : "validRefs",
        "expr" : "{$type: tutorials.notebook/RSpecIds$v1, result: (reduce( state = {$type: tutorials.notebook/ResolveRefsState$v1, context: specIds, resolved: []}; item in items ) { (if(item.refinesTo?( tutorials.notebook/NewSpec$v1 )) {({ 'new-spec' = item.refineTo( tutorials.notebook/NewSpec$v1 ); {$type: tutorials.notebook/ResolveRefsState$v1, context: state.context.conj('new-spec'.refineTo( tutorials.notebook/SpecId$v1 )), resolved: state.resolved} })} else {({ 'spec-ref' = item.refineTo( tutorials.notebook/SpecRef$v1 ); resolver = {$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: state.context, inputSpecRef: 'spec-ref', newSpecs: []}; resolved = (when(resolver.refinesTo?( tutorials.notebook/SpecId$v1 )) {resolver.refineTo( tutorials.notebook/SpecId$v1 )}); {$type: tutorials.notebook/ResolveRefsState$v1, context: state.context, resolved: (ifValue(resolved) {state.resolved.conj(resolved)} else {state.resolved})} })}) }).resolved}"
      }
    }
  },
  "tutorials.notebook/ResolveRefs$v1" : {
    "fields" : {
      "specIds" : [ "Vec", "tutorials.notebook/SpecId$v1" ],
      "items" : [ "Vec", "tutorials.notebook/AbstractNotebookItem$v1" ]
    },
    "constraints" : [ "{expr: ((filter(item in items)item.refinesTo?( tutorials.notebook/SpecRef$v1 )).count() == {$type: tutorials.notebook/ResolveRefsDirect$v1, items: items, specIds: specIds}.refineTo( tutorials.notebook/RSpecIds$v1 ).result.count()), name: \"allResolve\"}" ],
    "refines-to" : {
      "tutorials.notebook/RSpecIds$v1" : {
        "name" : "resolve",
        "expr" : "{$type: tutorials.notebook/ResolveRefsDirect$v1, items: items, specIds: specIds}.refineTo( tutorials.notebook/RSpecIds$v1 )"
      }
    }
  }
}
```

```java
(valid? {$type: tutorials.notebook/ResolveRefs$v1, items: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}]})


//-- result --
true
```

```java
(valid? {$type: tutorials.notebook/ResolveRefs$v1, items: [{$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 1, workspaceName: "my"}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}]})


//-- result --
true
```

```java
(valid? {$type: tutorials.notebook/ResolveRefs$v1, items: [{$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 3, workspaceName: "my"}, {$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 3, workspaceName: "my"}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}]})


//-- result --
true
```

```java
(valid? {$type: tutorials.notebook/ResolveRefs$v1, items: [{$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 3, workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 3, workspaceName: "my"}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}]})


//-- result --
false
```

```java
{$type: tutorials.notebook/ResolveRefs$v1, items: [{$type: tutorials.notebook/SpecRef$v1, specName: "A", workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 3, workspaceName: "my"}, {$type: tutorials.notebook/SpecRef$v1, specName: "A", workspaceName: "my"}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}]}.refineTo( tutorials.notebook/RSpecIds$v1 ).result


//-- result --
[{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 3, workspaceName: "my"}]
```

```java
{
  "tutorials.notebook/ApplicableNewSpecs$v1" : {
    "fields" : {
      "workspaceName" : "String",
      "specIds" : [ "Vec", "tutorials.notebook/SpecId$v1" ],
      "newSpecs" : [ "Vec", "tutorials.notebook/NewSpec$v1" ]
    },
    "constraints" : [ "{expr: ({ 'all-spec-names' = (reduce( a = #{}; ns in newSpecs ) { (if(a.contains?([ns.workspaceName, ns.specName])) {a} else {a.conj([ns.workspaceName, ns.specName])}) }); (every?(n in 'all-spec-names')({ 'max-version' = {$type: tutorials.notebook/FMaxSpecVersion$v1, specIds: specIds, specName: n[1], workspaceName: n[0]}.refineTo( tutorials.notebook/RInteger$v1 ).result; versions = [(ifValue('max-version') {'max-version'} else {0})].concat((map(ns in (filter(ns in newSpecs)((n[0] == ns.workspaceName) && (n[1] == ns.specName))))ns.specVersion)); (every?(pair in (map(i in range(0, (versions.count() - 1)))[versions[i], versions[(i + 1)]]))((pair[0] + 1) == pair[1])) })) }), name: \"newSpecsInOrder\"}", "{expr: ({ 'all-spec-names' = (reduce( a = #{}; ns in newSpecs ) { (if(a.contains?([ns.workspaceName, ns.specName])) {a} else {a.conj([ns.workspaceName, ns.specName])}) }); (every?(n in 'all-spec-names')({ 'ephemeral-values' = [false].concat((map(ns in (filter(ns in newSpecs)((n[0] == ns.workspaceName) && (n[1] == ns.specName))))({ 'is-e' = ns.isEphemeral; (ifValue('is-e') {true} else {false}) }))); (every?(pair in (map(i in range(0, ('ephemeral-values'.count() - 1)))['ephemeral-values'[i], 'ephemeral-values'[(i + 1)]]))(pair[1] || !pair[0])) })) }), name: \"nonEphemeralBuiltOnNonEphemeral\"}", "{expr: (0 == (filter(ns in newSpecs)!(ns.workspaceName == workspaceName)).count()), name: \"newSpecsInThisWorkspace\"}" ]
  }
}
```

```java
(valid? {$type: tutorials.notebook/ApplicableNewSpecs$v1, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 4, workspaceName: "my"}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], workspaceName: "my"})


//-- result --
false
```

```java
(valid? {$type: tutorials.notebook/ApplicableNewSpecs$v1, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "C", specVersion: 4, workspaceName: "my"}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], workspaceName: "my"})


//-- result --
false
```

```java
(valid? {$type: tutorials.notebook/ApplicableNewSpecs$v1, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 2, workspaceName: "my"}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], workspaceName: "my"})


//-- result --
false
```

```java
(valid? {$type: tutorials.notebook/ApplicableNewSpecs$v1, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 3, workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, specName: "B", specVersion: 2, workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 4, workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, specName: "C", specVersion: 1, workspaceName: "my"}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], workspaceName: "my"})


//-- result --
true
```

```java
(valid? {$type: tutorials.notebook/ApplicableNewSpecs$v1, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "C", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "C", specVersion: 2, workspaceName: "my"}], specIds: [], workspaceName: "my"})


//-- result --
true
```

Cannot create an non-ephemeral spec "on top" of an ephemeral spec.

```java
(valid? {$type: tutorials.notebook/ApplicableNewSpecs$v1, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "C", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, specName: "C", specVersion: 2, workspaceName: "my"}], specIds: [], workspaceName: "my"})


//-- result --
false
```

```java
{
  "tutorials.notebook/Effect$v1" : {
    "abstract?" : true
  },
  "tutorials.notebook/WriteSpecEffect$v1" : {
    "fields" : {
      "specId" : "tutorials.notebook/SpecId$v1"
    },
    "refines-to" : {
      "tutorials.notebook/Effect$v1" : {
        "name" : "effect",
        "expr" : "{$type: tutorials.notebook/Effect$v1}"
      }
    }
  },
  "tutorials.notebook/WriteNotebookEffect$v1" : {
    "fields" : {
      "notebookName" : "String",
      "notebookVersion" : "Integer"
    },
    "refines-to" : {
      "tutorials.notebook/Effect$v1" : {
        "name" : "effect",
        "expr" : "{$type: tutorials.notebook/Effect$v1}"
      }
    }
  },
  "tutorials.notebook/DeleteNotebookEffect$v1" : {
    "fields" : {
      "notebookName" : "String",
      "notebookVersion" : "Integer"
    },
    "refines-to" : {
      "tutorials.notebook/Effect$v1" : {
        "name" : "effect",
        "expr" : "{$type: tutorials.notebook/Effect$v1}"
      }
    }
  },
  "tutorials.notebook/WriteRegressionTestEffect$v1" : {
    "fields" : {
      "notebookName" : "String",
      "notebookVersion" : "Integer"
    },
    "refines-to" : {
      "tutorials.notebook/Effect$v1" : {
        "name" : "effect",
        "expr" : "{$type: tutorials.notebook/Effect$v1}"
      }
    }
  },
  "tutorials.notebook/DeleteRegressionTestEffect$v1" : {
    "fields" : {
      "notebookName" : "String",
      "notebookVersion" : "Integer"
    },
    "refines-to" : {
      "tutorials.notebook/Effect$v1" : {
        "name" : "effect",
        "expr" : "{$type: tutorials.notebook/Effect$v1}"
      }
    }
  },
  "tutorials.notebook/RunTestsEffect$v1" : {
    "fields" : {
      "notebookName" : "String",
      "notebookVersion" : "Integer",
      "registrySpecs" : [ "Maybe", "Boolean" ],
      "workspaceSpecs" : [ "Maybe", "Boolean" ]
    },
    "constraints" : [ "{expr: (((ifValue(registrySpecs) {registrySpecs} else {false}) || (ifValue(workspaceSpecs) {workspaceSpecs} else {false})) && !((ifValue(registrySpecs) {registrySpecs} else {false}) && (ifValue(workspaceSpecs) {workspaceSpecs} else {false}))), name: \"exclusiveFlags\"}", "{expr: (ifValue(registrySpecs) {registrySpecs} else {true}), name: \"registrySpecsFlag\"}", "{expr: (ifValue(workspaceSpecs) {workspaceSpecs} else {true}), name: \"workspaceSpecsFlag\"}" ],
    "refines-to" : {
      "tutorials.notebook/Effect$v1" : {
        "name" : "effect",
        "expr" : "{$type: tutorials.notebook/Effect$v1}"
      }
    }
  },
  "tutorials.notebook/WorkspaceAndEffects$v1" : {
    "fields" : {
      "workspace" : "tutorials.notebook/Workspace$v1",
      "effects" : [ "Vec", "tutorials.notebook/Effect$v1" ]
    }
  },
  "tutorials.notebook/WriteNotebook$v1" : {
    "fields" : {
      "workspace" : "tutorials.notebook/Workspace$v1",
      "notebookName" : "String",
      "notebookVersion" : "Integer",
      "notebookItems" : [ "Vec", "tutorials.notebook/AbstractNotebookItem$v1" ]
    },
    "constraints" : [ "{expr: (if((notebookVersion > 1)) {({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == (notebookVersion - 1)))); (filtered.count() == 1) })} else {true}), name: \"priorNotebookExists\"}", "{expr: (valid? {$type: tutorials.notebook/Version$v1, version: notebookVersion}), name: \"positiveVersion\"}" ],
    "refines-to" : {
      "tutorials.notebook/WorkspaceAndEffects$v1" : {
        "name" : "newWorkspaceAndEffects",
        "expr" : "{$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteNotebookEffect$v1, notebookName: notebookName, notebookVersion: notebookVersion}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: workspace.notebooks.conj({$type: tutorials.notebook/Notebook$v1, items: notebookItems, name: notebookName, version: notebookVersion}), registrySpecIds: workspace.registrySpecIds, specIds: workspace.specIds, tests: workspace.tests, workspaceName: workspace.workspaceName}}"
      }
    }
  },
  "tutorials.notebook/DeleteNotebook$v1" : {
    "fields" : {
      "workspace" : "tutorials.notebook/Workspace$v1",
      "notebookName" : "String",
      "notebookVersion" : "Integer"
    },
    "constraints" : [ "{expr: (if((notebookVersion > 1)) {({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (filtered.count() == 1) })} else {true}), name: \"notebookExists\"}", "{expr: (valid? {$type: tutorials.notebook/Version$v1, version: notebookVersion}), name: \"positiveVersion\"}" ],
    "refines-to" : {
      "tutorials.notebook/WorkspaceAndEffects$v1" : {
        "name" : "newWorkspaceAndEffects",
        "expr" : "{$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/DeleteNotebookEffect$v1, notebookName: notebookName, notebookVersion: notebookVersion}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: (filter(nb in workspace.notebooks)(!(nb.name == notebookName) || !(nb.version == notebookVersion))), registrySpecIds: workspace.registrySpecIds, specIds: workspace.specIds, tests: workspace.tests, workspaceName: workspace.workspaceName}}"
      }
    }
  },
  "tutorials.notebook/ApplyNotebook$v1" : {
    "fields" : {
      "workspace" : "tutorials.notebook/Workspace$v1",
      "notebookName" : "String",
      "notebookVersion" : "Integer"
    },
    "constraints" : [ "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); (valid? {$type: tutorials.notebook/ResolveRefs$v1, items: nb.items, specIds: workspace.specIds.concat(workspace.registrySpecIds)}) })} else {true}) }), name: \"specsValidRefs\"}", "{expr: ((filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))).count() > 0), name: \"notebookExists\"}", "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); ((filter(ns in (map(item in (filter(item in nb.items)item.refinesTo?( tutorials.notebook/NewSpec$v1 )))item.refineTo( tutorials.notebook/NewSpec$v1 )))({ 'is-ephemeral' = ns.isEphemeral; (ifValue('is-ephemeral') {false} else {true}) })).count() > 0) })} else {true}) }), name: \"notebookContainsNonEphemeralNewSpecs\"}", "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); (valid? {$type: tutorials.notebook/ApplicableNewSpecs$v1, newSpecs: (map(item in (filter(item in nb.items)item.refinesTo?( tutorials.notebook/NewSpec$v1 )))item.refineTo( tutorials.notebook/NewSpec$v1 )), specIds: workspace.specIds.concat(workspace.registrySpecIds), workspaceName: workspace.workspaceName}) })} else {true}) }), name: \"specsApplicable\"}" ],
    "refines-to" : {
      "tutorials.notebook/WorkspaceAndEffects$v1" : {
        "name" : "newWorkspaceAndEffects",
        "expr" : "({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (when((filtered.count() > 0)) {({ nb = filtered.first(); 'new-spec-ids' = (map(ns in (filter(ns in (map(item in (filter(item in nb.items)item.refinesTo?( tutorials.notebook/NewSpec$v1 )))item.refineTo( tutorials.notebook/NewSpec$v1 )))({ 'is-ephemeral' = ns.isEphemeral; (ifValue('is-ephemeral') {false} else {true}) })))ns.refineTo( tutorials.notebook/SpecId$v1 )); 'new-notebook' = {$type: tutorials.notebook/Notebook$v1, items: (filter(item in nb.items)(if(item.refinesTo?( tutorials.notebook/NewSpec$v1 )) {({ ns = item.refineTo( tutorials.notebook/NewSpec$v1 ); 'is-ephemeral' = ns.isEphemeral; (ifValue('is-ephemeral') {true} else {false}) })} else {true})), name: nb.name, version: (nb.version + 1)}; {$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: (map(si in 'new-spec-ids'){$type: tutorials.notebook/WriteSpecEffect$v1, specId: si}).concat([{$type: tutorials.notebook/WriteNotebookEffect$v1, notebookName: 'new-notebook'.name, notebookVersion: 'new-notebook'.version}, {$type: tutorials.notebook/RunTestsEffect$v1, notebookName: notebookName, notebookVersion: notebookVersion, workspaceSpecs: true}]), workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: (filter(nb in workspace.notebooks)((nb.name != notebookName) || (nb.version != notebookVersion))).conj('new-notebook'), registrySpecIds: workspace.registrySpecIds, specIds: workspace.specIds.concat('new-spec-ids'), tests: workspace.tests, workspaceName: workspace.workspaceName}} })}) })"
      }
    }
  },
  "tutorials.notebook/CreateRegressionTest$v1" : {
    "fields" : {
      "workspace" : "tutorials.notebook/Workspace$v1",
      "notebookName" : "String",
      "notebookVersion" : "Integer"
    },
    "constraints" : [ "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); (valid? {$type: tutorials.notebook/ResolveRefs$v1, items: nb.items, specIds: workspace.registrySpecIds}) })} else {true}) }), name: \"specsValidRefs\"}", "{expr: ((filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))).count() > 0), name: \"notebookExists\"}", "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); ((filter(ns in (map(item in (filter(item in nb.items)item.refinesTo?( tutorials.notebook/NewSpec$v1 )))item.refineTo( tutorials.notebook/NewSpec$v1 )))({ 'is-ephemeral' = ns.isEphemeral; (ifValue('is-ephemeral') {false} else {true}) })).count() == 0) })} else {true}) }), name: \"notebookCannotContainNewNonEphemeralSpecs\"}" ],
    "refines-to" : {
      "tutorials.notebook/WorkspaceAndEffects$v1" : {
        "name" : "newWorkspaceAndEffects",
        "expr" : "({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (when((filtered.count() > 0)) {({ nb = filtered.first(); 'new-test' = {$type: tutorials.notebook/RegressionTest$v1, notebookName: notebookName, notebookVersion: notebookVersion}; {$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteRegressionTestEffect$v1, notebookName: 'new-test'.notebookName, notebookVersion: 'new-test'.notebookVersion}, {$type: tutorials.notebook/RunTestsEffect$v1, notebookName: notebookName, notebookVersion: notebookVersion, registrySpecs: true}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: workspace.notebooks, registrySpecIds: workspace.registrySpecIds, specIds: workspace.specIds, tests: workspace.tests.conj('new-test'), workspaceName: workspace.workspaceName}} })}) })"
      }
    }
  },
  "tutorials.notebook/DeleteRegressionTest$v1" : {
    "fields" : {
      "workspace" : "tutorials.notebook/Workspace$v1",
      "notebookName" : "String",
      "notebookVersion" : "Integer"
    },
    "constraints" : [ "{expr: ((filter(t in workspace.tests)((t.notebookName == notebookName) && (t.notebookVersion == notebookVersion))).count() > 0), name: \"testExists\"}" ],
    "refines-to" : {
      "tutorials.notebook/WorkspaceAndEffects$v1" : {
        "name" : "newWorkspaceAndEffects",
        "expr" : "({ filtered = (filter(t in workspace.tests)((t.notebookName == notebookName) && (t.notebookVersion == notebookVersion))); ({ 'to-remove' = filtered.first(); {$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/DeleteRegressionTestEffect$v1, notebookName: 'to-remove'.notebookName, notebookVersion: 'to-remove'.notebookVersion}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: workspace.notebooks, registrySpecIds: workspace.registrySpecIds, specIds: workspace.specIds, tests: (filter(t in workspace.tests)(t != 'to-remove')), workspaceName: workspace.workspaceName}} }) })"
      }
    }
  },
  "tutorials.notebook/UpdateRegressionTest$v1" : {
    "fields" : {
      "workspace" : "tutorials.notebook/Workspace$v1",
      "notebookName" : "String",
      "notebookVersion" : "Integer",
      "lastNotebookVersion" : "Integer"
    },
    "constraints" : [ "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); (valid? {$type: tutorials.notebook/ResolveRefs$v1, items: nb.items, specIds: workspace.specIds.concat(workspace.registrySpecIds)}) })} else {true}) }), name: \"specsValidRefs\"}", "{expr: ((filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))).count() > 0), name: \"notebookExists\"}", "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); ((filter(ns in (map(item in (filter(item in nb.items)item.refinesTo?( tutorials.notebook/NewSpec$v1 )))item.refineTo( tutorials.notebook/NewSpec$v1 )))({ 'is-ephemeral' = ns.isEphemeral; (ifValue('is-ephemeral') {false} else {true}) })).count() == 0) })} else {true}) }), name: \"notebookCannotContainNewNonEphemeralSpecs\"}", "{expr: ((filter(t in workspace.tests)((t.notebookName == notebookName) && (t.notebookVersion == lastNotebookVersion))).count() > 0), name: \"testExists\"}" ],
    "refines-to" : {
      "tutorials.notebook/WorkspaceAndEffects$v1" : {
        "name" : "newWorkspaceAndEffects",
        "expr" : "({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (when((filtered.count() > 0)) {({ nb = filtered.first(); 'new-test' = {$type: tutorials.notebook/RegressionTest$v1, notebookName: notebookName, notebookVersion: notebookVersion}; {$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteRegressionTestEffect$v1, notebookName: 'new-test'.notebookName, notebookVersion: 'new-test'.notebookVersion}, {$type: tutorials.notebook/RunTestsEffect$v1, notebookName: notebookName, notebookVersion: notebookVersion, registrySpecs: true}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: workspace.notebooks, registrySpecIds: workspace.registrySpecIds, specIds: workspace.specIds, tests: (filter(t in workspace.tests)(t.notebookName != notebookName)).conj('new-test'), workspaceName: workspace.workspaceName}} })}) })"
      }
    }
  }
}
```

```java
({ ws = {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 3, workspaceName: "my"}], name: "notebook1", version: 1}], registrySpecIds: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], tests: [], workspaceName: "my"}; [(valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 1, workspace: ws}), (valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 2, workspace: ws}), (valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook2", notebookVersion: 1, workspace: ws})] })


//-- result --
[true, false, false]
```

If all of the new specs in the notebooks are ephemeral, then it cannot be applied.

```java
({ ws = {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "A", specVersion: 3, workspaceName: "my"}, {$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 3, workspaceName: "my"}], name: "notebook1", version: 1}], registrySpecIds: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], tests: [], workspaceName: "my"}; (valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 1, workspace: ws}) })


//-- result --
false
```

```java
({ ws = {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 1, workspaceName: "my"}], name: "notebook1", version: 1}], registrySpecIds: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], tests: [], workspaceName: "my"}; (valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 1, workspace: ws}) })


//-- result --
false
```

```java
{$type: tutorials.notebook/WriteNotebook$v1, notebookItems: [], notebookName: "notebook1", notebookVersion: 1, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [], registrySpecIds: [], specIds: [], tests: [], workspaceName: "my"}}.refineTo( tutorials.notebook/WorkspaceAndEffects$v1 )


//-- result --
{$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteNotebookEffect$v1, notebookName: "notebook1", notebookVersion: 1}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 1}], registrySpecIds: [], specIds: [], tests: [], workspaceName: "my"}}
```

```java
{$type: tutorials.notebook/DeleteNotebook$v1, notebookName: "notebook1", notebookVersion: 1, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 1}, {$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook2", version: 1}], registrySpecIds: [], specIds: [], tests: [], workspaceName: "my"}}.refineTo( tutorials.notebook/WorkspaceAndEffects$v1 )


//-- result --
{$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/DeleteNotebookEffect$v1, notebookName: "notebook1", notebookVersion: 1}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook2", version: 1}], registrySpecIds: [], specIds: [], tests: [], workspaceName: "my"}}
```

```java
{$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 1, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/NewSpec$v1, specName: "A", specVersion: 3, workspaceName: "my"}, {$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "C", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 3, workspaceName: "my"}], name: "notebook1", version: 1}, {$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/NewSpec$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecRef$v1, specName: "B", specVersion: 1, workspaceName: "my"}], name: "notebook2", version: 3}], registrySpecIds: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}], tests: [], workspaceName: "my"}}.refineTo( tutorials.notebook/WorkspaceAndEffects$v1 )


//-- result --
{$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteSpecEffect$v1, specId: {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 3, workspaceName: "my"}}, {$type: tutorials.notebook/WriteNotebookEffect$v1, notebookName: "notebook1", notebookVersion: 2}, {$type: tutorials.notebook/RunTestsEffect$v1, notebookName: "notebook1", notebookVersion: 1, workspaceSpecs: true}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/NewSpec$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecRef$v1, specName: "B", specVersion: 1, workspaceName: "my"}], name: "notebook2", version: 3}, {$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "C", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecRef$v1, specName: "A", specVersion: 3, workspaceName: "my"}], name: "notebook1", version: 2}], registrySpecIds: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "B", specVersion: 1, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 2, workspaceName: "my"}, {$type: tutorials.notebook/SpecId$v1, specName: "A", specVersion: 3, workspaceName: "my"}], tests: [], workspaceName: "my"}}
```

```java
{$type: tutorials.notebook/CreateRegressionTest$v1, notebookName: "notebook1", notebookVersion: 1, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 1}], registrySpecIds: [], specIds: [], tests: [], workspaceName: "my"}}.refineTo( tutorials.notebook/WorkspaceAndEffects$v1 )


//-- result --
{$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteRegressionTestEffect$v1, notebookName: "notebook1", notebookVersion: 1}, {$type: tutorials.notebook/RunTestsEffect$v1, notebookName: "notebook1", notebookVersion: 1, registrySpecs: true}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 1}], registrySpecIds: [], specIds: [], tests: [{$type: tutorials.notebook/RegressionTest$v1, notebookName: "notebook1", notebookVersion: 1}], workspaceName: "my"}}
```

```java
{$type: tutorials.notebook/CreateRegressionTest$v1, notebookName: "notebook1", notebookVersion: 2, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 2}], registrySpecIds: [], specIds: [], tests: [{$type: tutorials.notebook/RegressionTest$v1, notebookName: "notebook1", notebookVersion: 1}], workspaceName: "my"}}.refineTo( tutorials.notebook/WorkspaceAndEffects$v1 )


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/CreateRegressionTest$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueTestNames\""]
```

```java
{$type: tutorials.notebook/UpdateRegressionTest$v1, lastNotebookVersion: 1, notebookName: "notebook1", notebookVersion: 9, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 9}], registrySpecIds: [], specIds: [], tests: [{$type: tutorials.notebook/RegressionTest$v1, notebookName: "notebook1", notebookVersion: 1}], workspaceName: "my"}}.refineTo( tutorials.notebook/WorkspaceAndEffects$v1 )


//-- result --
{$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteRegressionTestEffect$v1, notebookName: "notebook1", notebookVersion: 9}, {$type: tutorials.notebook/RunTestsEffect$v1, notebookName: "notebook1", notebookVersion: 9, registrySpecs: true}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 9}], registrySpecIds: [], specIds: [], tests: [{$type: tutorials.notebook/RegressionTest$v1, notebookName: "notebook1", notebookVersion: 9}], workspaceName: "my"}}
```

```java
{$type: tutorials.notebook/DeleteRegressionTest$v1, notebookName: "notebook1", notebookVersion: 9, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 9}], registrySpecIds: [], specIds: [], tests: [{$type: tutorials.notebook/RegressionTest$v1, notebookName: "notebook1", notebookVersion: 9}, {$type: tutorials.notebook/RegressionTest$v1, notebookName: "notebook2", notebookVersion: 1}], workspaceName: "my"}}.refineTo( tutorials.notebook/WorkspaceAndEffects$v1 )


//-- result --
{$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/DeleteRegressionTestEffect$v1, notebookName: "notebook1", notebookVersion: 9}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 9}], registrySpecIds: [], specIds: [], tests: [{$type: tutorials.notebook/RegressionTest$v1, notebookName: "notebook2", notebookVersion: 1}], workspaceName: "my"}}
```


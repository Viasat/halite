<!---
  This markdown file was generated. Do not edit.
  -->

## Model a notebook mechanism



```java
{
  "tutorials.notebook/Version$v1" : {
    "fields" : {
      "version" : [ "Maybe", "Integer" ]
    },
    "constraints" : [ "{expr: (ifValue(version) {(version > 0)} else {true}), name: \"positiveVersion\"}" ]
  },
  "tutorials.notebook/SpecId$v1" : {
    "fields" : {
      "specName" : "String",
      "specVersion" : "Integer"
    },
    "constraints" : [ "{expr: (valid? {$type: tutorials.notebook/Version$v1, version: specVersion}), name: \"positiveVersion\"}" ]
  },
  "tutorials.notebook/AbstractNotebookItem$v1" : {
    "abstract?" : true
  },
  "tutorials.notebook/SpecRef$v1" : {
    "fields" : {
      "specName" : "String",
      "specVersion" : [ "Maybe", "Integer" ]
    },
    "constraints" : [ "{expr: (valid? {$type: tutorials.notebook/Version$v1, version: specVersion}), name: \"positiveVersion\"}" ],
    "refines-to" : {
      "tutorials.notebook/SpecId$v1" : {
        "name" : "specId",
        "expr" : "(whenValue(specVersion) {{$type: tutorials.notebook/SpecId$v1, specName: specName, specVersion: specVersion}})"
      },
      "tutorials.notebook/AbstractNotebookItem$v1" : {
        "name" : "abstractItems",
        "expr" : "{$type: tutorials.notebook/AbstractNotebookItem$v1}"
      }
    }
  }
}
```

```java
{$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}
```

```java
{$type: tutorials.notebook/SpecRef$v1, specName: "my/A"}
```

```java
{
  "tutorials.notebook/NewSpec$v1" : {
    "fields" : {
      "specName" : "String",
      "specVersion" : "Integer",
      "isEphemeral" : [ "Maybe", "Boolean" ]
    },
    "constraints" : [ "{expr: (ifValue(isEphemeral) {isEphemeral} else {true}), name: \"ephemeralFlag\"}", "{expr: (valid? {$type: tutorials.notebook/Version$v1, version: specVersion}), name: \"positiveVersion\"}" ],
    "refines-to" : {
      "tutorials.notebook/SpecId$v1" : {
        "name" : "specId",
        "expr" : "{$type: tutorials.notebook/SpecId$v1, specName: specName, specVersion: specVersion}"
      },
      "tutorials.notebook/AbstractNotebookItem$v1" : {
        "name" : "abstractItems",
        "expr" : "{$type: tutorials.notebook/AbstractNotebookItem$v1}"
      }
    }
  }
}
```

```java
{$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 1}
```

```java
{$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 0}


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""]
```

```java
{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "my/A", specVersion: 1}
```

```java
{$type: tutorials.notebook/NewSpec$v1, isEphemeral: false, specName: "my/A", specVersion: 1}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/NewSpec$v1', violates constraints \"tutorials.notebook/NewSpec$v1/ephemeralFlag\""]
```

```java
{$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 1}.refineTo( tutorials.notebook/SpecId$v1 )


//-- result --
{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}
```

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
      "specName" : "String"
    },
    "refines-to" : {
      "tutorials.notebook/RInteger$v1" : {
        "name" : "result",
        "expr" : "{$type: tutorials.notebook/RInteger$v1, result: ({ result = (reduce( a = 0; si in specIds ) { (if((si.specName != specName)) {a} else {(if((si.specVersion > a)) {si.specVersion} else {a})}) }); (when((0 != result)) {result}) })}"
      }
    }
  }
}
```

```java
{$type: tutorials.notebook/FMaxSpecVersion$v1, specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], specName: "my/A"}.refineTo( tutorials.notebook/RInteger$v1 )


//-- result --
{$type: tutorials.notebook/RInteger$v1, result: 2}
```

```java
{$type: tutorials.notebook/FMaxSpecVersion$v1, specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], specName: "my/B"}.refineTo( tutorials.notebook/RInteger$v1 )


//-- result --
{$type: tutorials.notebook/RInteger$v1, result: 1}
```

```java
{$type: tutorials.notebook/FMaxSpecVersion$v1, specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], specName: "my/C"}.refineTo( tutorials.notebook/RInteger$v1 )


//-- result --
{$type: tutorials.notebook/RInteger$v1}
```

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
        "expr" : "({ 'all-spec-ids' = existingSpecIds.concat((map(ns in newSpecs)ns.refineTo( tutorials.notebook/SpecId$v1 ))); 'spec-version' = inputSpecRef.specVersion; (ifValue('spec-version') {({ result = inputSpecRef.refineTo( tutorials.notebook/SpecId$v1 ); (when(#{}.concat('all-spec-ids').contains?(result)) {result}) })} else {({ 'max-version-in-context' = {$type: tutorials.notebook/FMaxSpecVersion$v1, specIds: 'all-spec-ids', specName: inputSpecRef.specName}.refineTo( tutorials.notebook/RInteger$v1 ).result; (whenValue('max-version-in-context') {{$type: tutorials.notebook/SpecId$v1, specName: inputSpecRef.specName, specVersion: 'max-version-in-context'}}) })}) })"
      }
    }
  }
}
```

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}, newSpecs: []}.refinesTo?( tutorials.notebook/SpecId$v1 )


//-- result --
false
```

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "my/A"}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "my/C", specVersion: 1}, {$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}]}.refineTo( tutorials.notebook/SpecId$v1 )


//-- result --
{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 3}
```

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "my/B"}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "my/C", specVersion: 1}, {$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}]}.refineTo( tutorials.notebook/SpecId$v1 )


//-- result --
{$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}
```

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "my/B", specVersion: 1}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "my/C", specVersion: 1}, {$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}]}.refineTo( tutorials.notebook/SpecId$v1 )


//-- result --
{$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}
```

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "my/C", specVersion: 1}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "my/C", specVersion: 1}, {$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}]}.refineTo( tutorials.notebook/SpecId$v1 )


//-- result --
{$type: tutorials.notebook/SpecId$v1, specName: "my/C", specVersion: 1}
```

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "my/C", specVersion: 2}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "my/C", specVersion: 1}, {$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}]}.refinesTo?( tutorials.notebook/SpecId$v1 )


//-- result --
false
```

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "my/X", specVersion: 1}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "my/C", specVersion: 1}, {$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}]}.refinesTo?( tutorials.notebook/SpecId$v1 )


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

A notebook contains spec references. This is modeled as the results of having parsed the references out of the contents of the notebook.

```java
{
  "tutorials.notebook/Notebook$v1" : {
    "fields" : {
      "name" : "String",
      "version" : "Integer",
      "items" : [ "Vec", "tutorials.notebook/AbstractNotebookItem$v1" ]
    },
    "constraints" : [ "{expr: (valid? {$type: tutorials.notebook/Version$v1, version: version}), name: \"positiveVersion\"}" ]
  },
  "tutorials.notebook/RegressionTest$v1" : {
    "fields" : {
      "notebookName" : "String",
      "notebookVersion" : "Integer"
    },
    "constraints" : [ "{expr: (valid? {$type: tutorials.notebook/Version$v1, version: notebookVersion}), name: \"positiveVersion\"}" ]
  },
  "tutorials.notebook/Workspace$v1" : {
    "fields" : {
      "specIds" : [ "Vec", "tutorials.notebook/SpecId$v1" ],
      "notebooks" : [ "Vec", "tutorials.notebook/Notebook$v1" ],
      "tests" : [ "Vec", "tutorials.notebook/RegressionTest$v1" ]
    },
    "constraints" : [ "{expr: (#{}.concat((map(n in notebooks)n.name)).count() == notebooks.count()), name: \"uniqueNotebookNames\"}", "{expr: (#{}.concat((map(t in tests)t.notebookName)).count() == tests.count()), name: \"uniqueTestNames\"}", "{expr: (#{}.concat(specIds).count() == specIds.count()), name: \"uniqueSpecIds\"}" ]
  }
}
```

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], tests: []}
```

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], tests: []}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/Workspace$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueSpecIds\""]
```

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 1}, {$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 2}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], tests: []}


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
(valid? {$type: tutorials.notebook/ResolveRefs$v1, items: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]})


//-- result --
true
```

```java
(valid? {$type: tutorials.notebook/ResolveRefs$v1, items: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]})


//-- result --
true
```

```java
(valid? {$type: tutorials.notebook/ResolveRefs$v1, items: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 3}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]})


//-- result --
true
```

```java
(valid? {$type: tutorials.notebook/ResolveRefs$v1, items: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]})


//-- result --
false
```

```java
{$type: tutorials.notebook/ResolveRefs$v1, items: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/A"}, {$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/A"}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}.refineTo( tutorials.notebook/RSpecIds$v1 ).result


//-- result --
[{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 3}]
```

```java
{
  "tutorials.notebook/ApplicableNewSpecs$v1" : {
    "fields" : {
      "specIds" : [ "Vec", "tutorials.notebook/SpecId$v1" ],
      "newSpecs" : [ "Vec", "tutorials.notebook/NewSpec$v1" ]
    },
    "constraints" : [ "{expr: ({ 'all-spec-names' = (reduce( a = #{}; ns in newSpecs ) { (if(a.contains?(ns.specName)) {a} else {a.conj(ns.specName)}) }); (every?(n in 'all-spec-names')({ 'max-version' = {$type: tutorials.notebook/FMaxSpecVersion$v1, specIds: specIds, specName: n}.refineTo( tutorials.notebook/RInteger$v1 ).result; versions = [(ifValue('max-version') {'max-version'} else {0})].concat((map(ns in (filter(ns in newSpecs)(n == ns.specName)))ns.specVersion)); (every?(pair in (map(i in range(0, (versions.count() - 1)))[versions[i], versions[(i + 1)]]))((pair[0] + 1) == pair[1])) })) }), name: \"newSpecsInOrder\"}" ]
  }
}
```

```java
(valid? {$type: tutorials.notebook/ApplicableNewSpecs$v1, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 4}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]})


//-- result --
false
```

```java
(valid? {$type: tutorials.notebook/ApplicableNewSpecs$v1, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/C", specVersion: 4}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]})


//-- result --
false
```

```java
(valid? {$type: tutorials.notebook/ApplicableNewSpecs$v1, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 2}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]})


//-- result --
false
```

```java
(valid? {$type: tutorials.notebook/ApplicableNewSpecs$v1, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/NewSpec$v1, specName: "my/B", specVersion: 2}, {$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 4}, {$type: tutorials.notebook/NewSpec$v1, specName: "my/C", specVersion: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]})


//-- result --
true
```

```java
{
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
  "tutorials.notebook/UpdateRegressionTest$v1" : {
    "fields" : {
      "workspace" : "tutorials.notebook/Workspace$v1",
      "notebookName" : "String",
      "notebookVersion" : "Integer",
      "lastNotebookVersion" : "Integer"
    },
    "constraints" : [ "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); (valid? {$type: tutorials.notebook/ResolveRefs$v1, items: nb.items, specIds: workspace.specIds}) })} else {true}) }), name: \"specsValidRefs\"}", "{expr: ((filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))).count() > 0), name: \"notebookExists\"}", "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); ((filter(ns in (map(item in (filter(item in nb.items)item.refinesTo?( tutorials.notebook/NewSpec$v1 )))item.refineTo( tutorials.notebook/NewSpec$v1 )))({ 'is-ephemeral' = ns.isEphemeral; (ifValue('is-ephemeral') {false} else {true}) })).count() == 0) })} else {true}) }), name: \"notebookCannotContainNewNonEphemeralSpecs\"}", "{expr: ((filter(t in workspace.tests)((t.notebookName == notebookName) && (t.notebookVersion == lastNotebookVersion))).count() > 0), name: \"testExists\"}" ],
    "refines-to" : {
      "tutorials.notebook/WorkspaceAndEffects$v1" : {
        "name" : "newWorkspaceAndEffects",
        "expr" : "({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (when((filtered.count() > 0)) {({ nb = filtered.first(); 'new-test' = {$type: tutorials.notebook/RegressionTest$v1, notebookName: notebookName, notebookVersion: notebookVersion}; {$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteRegressionTestEffect$v1, notebookName: 'new-test'.notebookName, notebookVersion: 'new-test'.notebookVersion}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: workspace.notebooks, specIds: workspace.specIds, tests: (filter(t in workspace.tests)(t.notebookName != notebookName)).conj('new-test')}} })}) })"
      }
    }
  },
  "tutorials.notebook/ApplyNotebook$v1" : {
    "fields" : {
      "workspace" : "tutorials.notebook/Workspace$v1",
      "notebookName" : "String",
      "notebookVersion" : "Integer"
    },
    "constraints" : [ "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); (valid? {$type: tutorials.notebook/ResolveRefs$v1, items: nb.items, specIds: workspace.specIds}) })} else {true}) }), name: \"specsValidRefs\"}", "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); (valid? {$type: tutorials.notebook/ApplicableNewSpecs$v1, newSpecs: (map(item in (filter(item in nb.items)item.refinesTo?( tutorials.notebook/NewSpec$v1 )))item.refineTo( tutorials.notebook/NewSpec$v1 )), specIds: workspace.specIds}) })} else {true}) }), name: \"specsApplicable\"}", "{expr: ((filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))).count() > 0), name: \"notebookExists\"}", "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); ((filter(ns in (map(item in (filter(item in nb.items)item.refinesTo?( tutorials.notebook/NewSpec$v1 )))item.refineTo( tutorials.notebook/NewSpec$v1 )))({ 'is-ephemeral' = ns.isEphemeral; (ifValue('is-ephemeral') {false} else {true}) })).count() > 0) })} else {true}) }), name: \"notebookContainsNonEphemeralNewSpecs\"}" ],
    "refines-to" : {
      "tutorials.notebook/WorkspaceAndEffects$v1" : {
        "name" : "newWorkspaceAndEffects",
        "expr" : "({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (when((filtered.count() > 0)) {({ nb = filtered.first(); 'new-spec-ids' = (map(ns in (filter(ns in (map(item in (filter(item in nb.items)item.refinesTo?( tutorials.notebook/NewSpec$v1 )))item.refineTo( tutorials.notebook/NewSpec$v1 )))({ 'is-ephemeral' = ns.isEphemeral; (ifValue('is-ephemeral') {false} else {true}) })))ns.refineTo( tutorials.notebook/SpecId$v1 )); 'new-notebook' = {$type: tutorials.notebook/Notebook$v1, items: (filter(item in nb.items)(if(item.refinesTo?( tutorials.notebook/NewSpec$v1 )) {({ ns = item.refineTo( tutorials.notebook/NewSpec$v1 ); 'is-ephemeral' = ns.isEphemeral; (ifValue('is-ephemeral') {true} else {false}) })} else {true})), name: nb.name, version: (nb.version + 1)}; {$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: (map(si in 'new-spec-ids'){$type: tutorials.notebook/WriteSpecEffect$v1, specId: si}).conj({$type: tutorials.notebook/WriteNotebookEffect$v1, notebookName: 'new-notebook'.name, notebookVersion: 'new-notebook'.version}), workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: (filter(nb in workspace.notebooks)((nb.name != notebookName) || (nb.version != notebookVersion))).conj('new-notebook'), specIds: workspace.specIds.concat('new-spec-ids'), tests: workspace.tests}} })}) })"
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
        "expr" : "{$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteNotebookEffect$v1, notebookName: notebookName, notebookVersion: notebookVersion}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: workspace.notebooks.conj({$type: tutorials.notebook/Notebook$v1, items: notebookItems, name: notebookName, version: notebookVersion}), specIds: workspace.specIds, tests: workspace.tests}}"
      }
    }
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
  "tutorials.notebook/Effect$v1" : {
    "abstract?" : true
  },
  "tutorials.notebook/CreateRegressionTest$v1" : {
    "fields" : {
      "workspace" : "tutorials.notebook/Workspace$v1",
      "notebookName" : "String",
      "notebookVersion" : "Integer"
    },
    "constraints" : [ "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); (valid? {$type: tutorials.notebook/ResolveRefs$v1, items: nb.items, specIds: workspace.specIds}) })} else {true}) }), name: \"specsValidRefs\"}", "{expr: ((filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))).count() > 0), name: \"notebookExists\"}", "{expr: ({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (if((filtered.count() > 0)) {({ nb = filtered.first(); ((filter(ns in (map(item in (filter(item in nb.items)item.refinesTo?( tutorials.notebook/NewSpec$v1 )))item.refineTo( tutorials.notebook/NewSpec$v1 )))({ 'is-ephemeral' = ns.isEphemeral; (ifValue('is-ephemeral') {false} else {true}) })).count() == 0) })} else {true}) }), name: \"notebookCannotContainNewNonEphemeralSpecs\"}" ],
    "refines-to" : {
      "tutorials.notebook/WorkspaceAndEffects$v1" : {
        "name" : "newWorkspaceAndEffects",
        "expr" : "({ filtered = (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion))); (when((filtered.count() > 0)) {({ nb = filtered.first(); 'new-test' = {$type: tutorials.notebook/RegressionTest$v1, notebookName: notebookName, notebookVersion: notebookVersion}; {$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteRegressionTestEffect$v1, notebookName: 'new-test'.notebookName, notebookVersion: 'new-test'.notebookVersion}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: workspace.notebooks, specIds: workspace.specIds, tests: workspace.tests.conj('new-test')}} })}) })"
      }
    }
  },
  "tutorials.notebook/WorkspaceAndEffects$v1" : {
    "fields" : {
      "workspace" : "tutorials.notebook/Workspace$v1",
      "effects" : [ "Vec", "tutorials.notebook/Effect$v1" ]
    }
  }
}
```

```java
({ ws = {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}], name: "notebook1", version: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], tests: []}; [(valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 1, workspace: ws}), (valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 2, workspace: ws}), (valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook2", notebookVersion: 1, workspace: ws})] })


//-- result --
[true, false, false]
```

If all of the new specs in the notebooks are ephemeral, then it cannot be applied.

```java
({ ws = {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 3}], name: "notebook1", version: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], tests: []}; (valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 1, workspace: ws}) })


//-- result --
false
```

```java
({ ws = {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}], name: "notebook1", version: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], tests: []}; (valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 1, workspace: ws}) })


//-- result --
false
```

```java
{$type: tutorials.notebook/WriteNotebook$v1, notebookItems: [], notebookName: "notebook1", notebookVersion: 1, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [], specIds: [], tests: []}}.refineTo( tutorials.notebook/WorkspaceAndEffects$v1 )


//-- result --
{$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteNotebookEffect$v1, notebookName: "notebook1", notebookVersion: 1}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 1}], specIds: [], tests: []}}
```

```java
{$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 1, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "my/C", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 3}], name: "notebook1", version: 1}, {$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/B", specVersion: 1}], name: "notebook2", version: 3}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], tests: []}}.refineTo( tutorials.notebook/WorkspaceAndEffects$v1 )


//-- result --
{$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteSpecEffect$v1, specId: {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 3}}, {$type: tutorials.notebook/WriteNotebookEffect$v1, notebookName: "notebook1", notebookVersion: 2}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/B", specVersion: 1}], name: "notebook2", version: 3}, {$type: tutorials.notebook/Notebook$v1, items: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "my/C", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 3}], name: "notebook1", version: 2}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 3}], tests: []}}
```

```java
{$type: tutorials.notebook/CreateRegressionTest$v1, notebookName: "notebook1", notebookVersion: 1, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 1}], specIds: [], tests: []}}.refineTo( tutorials.notebook/WorkspaceAndEffects$v1 )


//-- result --
{$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteRegressionTestEffect$v1, notebookName: "notebook1", notebookVersion: 1}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 1}], specIds: [], tests: [{$type: tutorials.notebook/RegressionTest$v1, notebookName: "notebook1", notebookVersion: 1}]}}
```

```java
{$type: tutorials.notebook/CreateRegressionTest$v1, notebookName: "notebook1", notebookVersion: 2, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 2}], specIds: [], tests: [{$type: tutorials.notebook/RegressionTest$v1, notebookName: "notebook1", notebookVersion: 1}]}}.refineTo( tutorials.notebook/WorkspaceAndEffects$v1 )


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/CreateRegressionTest$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueTestNames\""]
```

```java
{$type: tutorials.notebook/UpdateRegressionTest$v1, lastNotebookVersion: 1, notebookName: "notebook1", notebookVersion: 9, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 9}], specIds: [], tests: [{$type: tutorials.notebook/RegressionTest$v1, notebookName: "notebook1", notebookVersion: 1}]}}.refineTo( tutorials.notebook/WorkspaceAndEffects$v1 )


//-- result --
{$type: tutorials.notebook/WorkspaceAndEffects$v1, effects: [{$type: tutorials.notebook/WriteRegressionTestEffect$v1, notebookName: "notebook1", notebookVersion: 9}], workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, items: [], name: "notebook1", version: 9}], specIds: [], tests: [{$type: tutorials.notebook/RegressionTest$v1, notebookName: "notebook1", notebookVersion: 9}]}}
```


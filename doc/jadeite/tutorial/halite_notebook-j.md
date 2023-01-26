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
        "expr" : "({ 'all-spec-ids' = existingSpecIds.concat(map(ns in newSpecs)ns.refineTo( tutorials.notebook/SpecId$v1 )); 'spec-version' = inputSpecRef.specVersion; (ifValue('spec-version') {({ result = inputSpecRef.refineTo( tutorials.notebook/SpecId$v1 ); (when(#{}.concat('all-spec-ids').contains?(result)) {result}) })} else {({ 'max-version-in-context' = {$type: tutorials.notebook/FMaxSpecVersion$v1, specIds: 'all-spec-ids', specName: inputSpecRef.specName}.refineTo( tutorials.notebook/RInteger$v1 ).result; (whenValue('max-version-in-context') {{$type: tutorials.notebook/SpecId$v1, specName: inputSpecRef.specName, specVersion: 'max-version-in-context'}}) })}) })"
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
      "newSpecs" : [ "Vec", "tutorials.notebook/NewSpec$v1" ],
      "specRefs" : [ "Vec", "tutorials.notebook/SpecRef$v1" ]
    },
    "constraints" : [ "{expr: (valid? {$type: tutorials.notebook/Version$v1, version: version}), name: \"positiveVersion\"}" ]
  },
  "tutorials.notebook/Workspace$v1" : {
    "fields" : {
      "specIds" : [ "Vec", "tutorials.notebook/SpecId$v1" ],
      "notebooks" : [ "Vec", "tutorials.notebook/Notebook$v1" ]
    },
    "constraints" : [ "{expr: (#{}.concat(map(n in notebooks)n.name).count() == notebooks.count()), name: \"uniqueNotebookNames\"}", "{expr: (#{}.concat(specIds).count() == specIds.count()), name: \"uniqueSpecIds\"}" ]
  }
}
```

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, name: "notebook1", newSpecs: [], specRefs: [], version: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}
```

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, name: "notebook1", newSpecs: [], specRefs: [], version: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/Workspace$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueSpecIds\""]
```

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, name: "notebook1", newSpecs: [], specRefs: [], version: 1}, {$type: tutorials.notebook/Notebook$v1, name: "notebook1", newSpecs: [], specRefs: [], version: 2}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/Workspace$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueNotebookNames\""]
```

```java
{
  "tutorials.notebook/ValidRefs$v1" : {
    "fields" : {
      "specIds" : [ "Vec", "tutorials.notebook/SpecId$v1" ],
      "newSpecs" : [ "Vec", "tutorials.notebook/NewSpec$v1" ],
      "specRefs" : [ "Vec", "tutorials.notebook/SpecRef$v1" ]
    },
    "constraints" : [ "{expr: every?(sr in specRefs){$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: specIds, inputSpecRef: sr, newSpecs: newSpecs}.refinesTo?( tutorials.notebook/SpecId$v1 ), name: \"validRefs\"}" ]
  }
}
```

```java
(valid? {$type: tutorials.notebook/ValidRefs$v1, newSpecs: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], specRefs: []})


//-- result --
true
```

```java
(valid? {$type: tutorials.notebook/ValidRefs$v1, newSpecs: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], specRefs: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}]})


//-- result --
true
```

```java
(valid? {$type: tutorials.notebook/ValidRefs$v1, newSpecs: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], specRefs: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/X", specVersion: 1}]})


//-- result --
false
```

```java
{
  "tutorials.notebook/ApplicableNewSpecs$v1" : {
    "fields" : {
      "specIds" : [ "Vec", "tutorials.notebook/SpecId$v1" ],
      "newSpecs" : [ "Vec", "tutorials.notebook/NewSpec$v1" ]
    },
    "constraints" : [ "{expr: ({ 'all-spec-names' = (reduce( a = #{}; ns in newSpecs ) { (if(a.contains?(ns.specName)) {a} else {a.conj(ns.specName)}) }); every?(n in 'all-spec-names')({ 'max-version' = {$type: tutorials.notebook/FMaxSpecVersion$v1, specIds: specIds, specName: n}.refineTo( tutorials.notebook/RInteger$v1 ).result; versions = [(ifValue('max-version') {'max-version'} else {0})].concat(map(ns in filter(ns in newSpecs)(n == ns.specName))ns.specVersion); every?(pair in map(i in range(0, (versions.count() - 1)))[versions[i], versions[(i + 1)]])((pair[0] + 1) == pair[1]) }) }), name: \"newSpecsInOrder\"}" ]
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
  "tutorials.notebook/ApplyNotebook$v1" : {
    "fields" : {
      "workspace" : "tutorials.notebook/Workspace$v1",
      "notebookName" : "String",
      "notebookVersion" : "Integer"
    },
    "constraints" : [ "{expr: ({ filtered = filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion)); (if((filtered.count() > 0)) {({ nb = filtered.first(); (valid? {$type: tutorials.notebook/ValidRefs$v1, newSpecs: nb.newSpecs, specIds: workspace.specIds, specRefs: nb.specRefs}) })} else {true}) }), name: \"specsValidRefs\"}", "{expr: (filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion)).count() > 0), name: \"notebookExists\"}", "{expr: ({ filtered = filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion)); (if((filtered.count() > 0)) {({ nb = filtered.first(); (nb.newSpecs.count() > 0) })} else {true}) }), name: \"notebookContainsNewSpecs\"}", "{expr: ({ filtered = filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion)); (if((filtered.count() > 0)) {({ nb = filtered.first(); (valid? {$type: tutorials.notebook/ApplicableNewSpecs$v1, newSpecs: nb.newSpecs, specIds: workspace.specIds}) })} else {true}) }), name: \"specsApplicable\"}" ],
    "refines-to" : {
      "tutorials.notebook/Workspace$v1" : {
        "name" : "newWorkspace",
        "expr" : "({ filtered = filter(nb in workspace.notebooks)((nb.name == notebookName) && (nb.version == notebookVersion)); (when((filtered.count() > 0)) {({ nb = filtered.first(); {$type: tutorials.notebook/Workspace$v1, notebooks: filter(nb in workspace.notebooks)((nb.name != notebookName) || (nb.version != notebookVersion)).conj({$type: tutorials.notebook/Notebook$v1, name: nb.name, newSpecs: [], specRefs: nb.specRefs, version: (nb.version + 1)}), specIds: workspace.specIds.concat(map(ns in nb.newSpecs)ns.refineTo( tutorials.notebook/SpecId$v1 ))} })}) })"
      }
    }
  }
}
```

```java
({ ws = {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, name: "notebook1", newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}], specRefs: [], version: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}; [(valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 1, workspace: ws}), (valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 2, workspace: ws}), (valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook2", notebookVersion: 1, workspace: ws})] })


//-- result --
[true, false, false]
```

```java
({ ws = {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, name: "notebook1", newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}], specRefs: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 3}], version: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}; (valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 1, workspace: ws}) })


//-- result --
true
```

```java
({ ws = {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, name: "notebook1", newSpecs: [], specRefs: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}], version: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}; (valid? {$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 1, workspace: ws}) })


//-- result --
false
```

```java
{$type: tutorials.notebook/ApplyNotebook$v1, notebookName: "notebook1", notebookVersion: 1, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, name: "notebook1", newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}], specRefs: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 3}], version: 1}, {$type: tutorials.notebook/Notebook$v1, name: "notebook2", newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/B", specVersion: 1}], specRefs: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/B", specVersion: 1}], version: 3}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}}.refineTo( tutorials.notebook/Workspace$v1 )


//-- result --
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, name: "notebook2", newSpecs: [{$type: tutorials.notebook/NewSpec$v1, specName: "my/B", specVersion: 1}], specRefs: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/B", specVersion: 1}], version: 3}, {$type: tutorials.notebook/Notebook$v1, name: "notebook1", newSpecs: [], specRefs: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 3}], version: 2}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 3}]}
```


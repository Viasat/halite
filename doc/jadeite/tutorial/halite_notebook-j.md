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
        "expr" : "({ 'all-spec-ids' = existingSpecIds.concat(map(ns in newSpecs)ns.refineTo( tutorials.notebook/SpecId$v1 )); 'spec-name' = inputSpecRef.specVersion; (ifValue('spec-name') {({ result = inputSpecRef.refineTo( tutorials.notebook/SpecId$v1 ); (if(#{}.concat('all-spec-ids').contains?(result)) {result} else {error(str(\"fixed ref does not resolve: \", inputSpecRef.specName))}) })} else {{$type: tutorials.notebook/SpecId$v1, specName: inputSpecRef.specName, specVersion: ({ 'max-version-in-context' = {$type: tutorials.notebook/FMaxSpecVersion$v1, specIds: 'all-spec-ids', specName: inputSpecRef.specName}.refineTo( tutorials.notebook/RInteger$v1 ).result; (ifValue('max-version-in-context') {'max-version-in-context'} else {error(str(\"floating ref does not resolve: \", inputSpecRef.specName))}) })}}) })"
      }
    }
  }
}
```

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}, newSpecs: []}.refineTo( tutorials.notebook/SpecId$v1 )


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"fixed ref does not resolve: my/A\""]
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
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "my/C", specVersion: 2}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "my/C", specVersion: 1}, {$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}]}.refineTo( tutorials.notebook/SpecId$v1 )


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"fixed ref does not resolve: my/C\""]
```

```java
{$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}], inputSpecRef: {$type: tutorials.notebook/SpecRef$v1, specName: "my/X", specVersion: 1}, newSpecs: [{$type: tutorials.notebook/NewSpec$v1, isEphemeral: true, specName: "my/C", specVersion: 1}, {$type: tutorials.notebook/NewSpec$v1, specName: "my/A", specVersion: 3}]}.refineTo( tutorials.notebook/SpecId$v1 )


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"fixed ref does not resolve: my/X\""]
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
      "contents" : "String",
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
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "contents1", name: "notebook1", newSpecs: [], specRefs: [], version: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}
```

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "contents1", name: "notebook1", newSpecs: [], specRefs: [], version: 1}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/Workspace$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueSpecIds\""]
```

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "contents1", name: "notebook1", newSpecs: [], specRefs: [], version: 1}, {$type: tutorials.notebook/Notebook$v1, contents: "contents1", name: "notebook1", newSpecs: [], specRefs: [], version: 2}], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/Workspace$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueNotebookNames\""]
```

```java
{
  "tutorials.notebook/RBoolean$v1" : {
    "fields" : {
      "result" : "Boolean"
    }
  },
  "tutorials.notebook/FAreNotebookRefsValid$v1" : {
    "fields" : {
      "workspace" : "tutorials.notebook/Workspace$v1",
      "notebook" : "tutorials.notebook/Notebook$v1"
    },
    "refines-to" : {
      "tutorials.notebook/RBoolean$v1" : {
        "name" : "result",
        "expr" : "{$type: tutorials.notebook/RBoolean$v1, result: every?(sr in notebook.specRefs)({ '_' = {$type: tutorials.notebook/SpecRefResolver$v1, existingSpecIds: workspace.specIds, inputSpecRef: sr, newSpecs: notebook.newSpecs}.refineTo( tutorials.notebook/SpecId$v1 ); true })}"
      }
    }
  }
}
```

```java
{$type: tutorials.notebook/FAreNotebookRefsValid$v1, notebook: {$type: tutorials.notebook/Notebook$v1, contents: "contents1", name: "notebook1", newSpecs: [], specRefs: [], version: 1}, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}}.refineTo( tutorials.notebook/RBoolean$v1 ).result


//-- result --
true
```

```java
{$type: tutorials.notebook/FAreNotebookRefsValid$v1, notebook: {$type: tutorials.notebook/Notebook$v1, contents: "contents1", name: "notebook1", newSpecs: [], specRefs: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/A", specVersion: 1}], version: 1}, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}}.refineTo( tutorials.notebook/RBoolean$v1 ).result


//-- result --
true
```

```java
{$type: tutorials.notebook/FAreNotebookRefsValid$v1, notebook: {$type: tutorials.notebook/Notebook$v1, contents: "contents1", name: "notebook1", newSpecs: [], specRefs: [{$type: tutorials.notebook/SpecRef$v1, specName: "my/X", specVersion: 1}], version: 1}, workspace: {$type: tutorials.notebook/Workspace$v1, notebooks: [], specIds: [{$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/B", specVersion: 1}, {$type: tutorials.notebook/SpecId$v1, specName: "my/A", specVersion: 2}]}}.refineTo( tutorials.notebook/RBoolean$v1 ).result


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"fixed ref does not resolve: my/X\""]
```


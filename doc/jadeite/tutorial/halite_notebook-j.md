<!---
  This markdown file was generated. Do not edit.
  -->

## Model a notebook mechanism



Model a notebook

Abstractly a spec ref contains the following fields.

```java
{
  "tutorials.notebook/SpecRef$v1" : {
    "abstract?" : true,
    "fields" : {
      "specName" : "String",
      "specVersion" : "Integer",
      "isFloating" : "Boolean",
      "isNew" : "Boolean"
    }
  }
}
```

A fixed reference contains in itself the specific version of the spec it is referencing. This spec must either already exist in the workpsace or it must have been created earlier in the notebook.

```java
{
  "tutorials.notebook/FixedSpecRef$v1" : {
    "fields" : {
      "specName" : "String",
      "specVersion" : "Integer"
    },
    "refines-to" : {
      "tutorials.notebook/SpecRef$v1" : {
        "name" : "toSpecRef",
        "expr" : "{$type: tutorials.notebook/SpecRef$v1, isFloating: false, isNew: false, specName: specName, specVersion: specVersion}"
      }
    }
  }
}
```

A floating reference just points to whatever the latest spec is that is in scope at the time the reference is processed. This might be a spec created earlier in the notebook.

```java
{
  "tutorials.notebook/FloatingSpecRef$v1" : {
    "fields" : {
      "specName" : "String"
    },
    "refines-to" : {
      "tutorials.notebook/SpecRef$v1" : {
        "name" : "toSpecRef",
        "expr" : "{$type: tutorials.notebook/SpecRef$v1, isFloating: true, isNew: false, specName: specName, specVersion: 0}"
      }
    }
  }
}
```

The creation of a new spec of a given version is treated as a spec reference in the notebook.

```java
{
  "tutorials.notebook/NewSpecRef$v1" : {
    "fields" : {
      "specName" : "String",
      "specVersion" : "Integer"
    },
    "refines-to" : {
      "tutorials.notebook/SpecRef$v1" : {
        "name" : "toSpecRef",
        "expr" : "{$type: tutorials.notebook/SpecRef$v1, isFloating: false, isNew: true, specName: specName, specVersion: specVersion}"
      }
    }
  }
}
```

All the spec refs above need to be resolved to fixed spec references for processing. This resolver takes in all of the references that exist in a workspace context, the references from the notebook being processed, and a specific reference from the notebook. Via refinement, it produces a fixed spec reference that should be used instead in the given context.

```java
{
  "tutorials.notebook/SpecRefResolver$v1" : {
    "fields" : {
      "workspaceSpecRefs" : [ "Vec", "tutorials.notebook/FixedSpecRef$v1" ],
      "notebookSpecRefs" : [ "Vec", "tutorials.notebook/SpecRef$v1" ],
      "inputSpecRef" : "tutorials.notebook/SpecRef$v1"
    },
    "refines-to" : {
      "tutorials.notebook/FixedSpecRef$v1" : {
        "name" : "toFixedSpecRef",
        "expr" : "({ inputSpecRef = inputSpecRef.refineTo( tutorials.notebook/SpecRef$v1 ); 'new-spec-refs' = filter(sr in map(sr in notebookSpecRefs)sr.refineTo( tutorials.notebook/SpecRef$v1 ))sr.isNew; 'all-spec-refs' = workspaceSpecRefs.concat(map(sr in 'new-spec-refs'){$type: tutorials.notebook/FixedSpecRef$v1, specName: sr.specName, specVersion: sr.specVersion}); 'max-version-in-workspace' = (reduce( a = 0; sr in workspaceSpecRefs ) { (if((sr.specName != inputSpecRef.specName)) {a} else {(if((sr.specVersion > a)) {sr.specVersion} else {a})}) }); 'max-version-in-context' = (reduce( a = 0; sr in 'all-spec-refs' ) { (if((sr.specName != inputSpecRef.specName)) {a} else {(if((sr.specVersion > a)) {sr.specVersion} else {a})}) }); (if(inputSpecRef.isFloating) {{$type: tutorials.notebook/FixedSpecRef$v1, specName: inputSpecRef.specName, specVersion: (if(('max-version-in-context' == 0)) {error(str(\"floating ref does not resolve: \", inputSpecRef.specName))} else {'max-version-in-context'})}} else {(if(inputSpecRef.isNew) {{$type: tutorials.notebook/FixedSpecRef$v1, specName: inputSpecRef.specName, specVersion: ({ v = (reduce( a = 0; sr in workspaceSpecRefs ) { (if((sr.specName != inputSpecRef.specName)) {a} else {(if((sr.specVersion > a)) {sr.specVersion} else {a})}) }); 'in-sequence?' = (reduce( a = 'max-version-in-workspace'; v in map(sr in filter(sr in 'new-spec-refs')(sr.specName == inputSpecRef.specName))sr.specVersion ) { (if(((a + 1) == v)) {v} else {error(str(\"new versions not in sequence: \", inputSpecRef.specName))}) }); (if((((v == 0) && !(1 == inputSpecRef.specVersion)) || !(inputSpecRef.specVersion > v))) {error(str(\"CAS error on new ref: \", inputSpecRef.specName))} else {inputSpecRef.specVersion}) })}} else {{$type: tutorials.notebook/FixedSpecRef$v1, specName: inputSpecRef.specName, specVersion: ({ 'found?' = (reduce( a = false; sr in 'all-spec-refs' ) { (a || ((sr.specName == inputSpecRef.specName) && (sr.specVersion == inputSpecRef.specVersion)) || a) }); (if('found?') {inputSpecRef.specVersion} else {error(str(\"fixed ref not resolve: \", inputSpecRef.specName))}) })}})}) })"
      }
    }
  }
}
```

Make a spec to hold the result of resolving all spec references in a notebook.

```java
{
  "tutorials.notebook/ResolvedSpecRefs$v1" : {
    "fields" : {
      "specRefs" : [ "Vec", "tutorials.notebook/FixedSpecRef$v1" ]
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
      "specRefs" : [ "Vec", "tutorials.notebook/SpecRef$v1" ]
    },
    "constraints" : [ "{expr: (version > 0), name: \"positiveVersion\"}" ]
  }
}
```

A workspace contains its own spec references. This represents the spec references contained in the registry for this workspace and its registry. In addition, the workspace contains notebooks. The workspace is only valid if all of the notebooks it contains are valid. Via refinement, the workspace can be "queried" to produce all of the resolved spec references from all of the notebooks.

```java
{
  "tutorials.notebook/Workspace$v1" : {
    "fields" : {
      "specRefs" : [ "Vec", "tutorials.notebook/FixedSpecRef$v1" ],
      "notebooks" : [ "Vec", "tutorials.notebook/Notebook$v1" ]
    },
    "constraints" : [ "{expr: every?(n in notebooks)every?(sr in n.specRefs){$type: tutorials.notebook/SpecRefResolver$v1, inputSpecRef: sr, notebookSpecRefs: n.specRefs, workspaceSpecRefs: specRefs}.refinesTo?( tutorials.notebook/FixedSpecRef$v1 ), name: \"validRefs\"}", "{expr: (#{}.concat(map(n in notebooks)n.name).count() == notebooks.count()), name: \"uniqueNotebookNames\"}" ],
    "refines-to" : {
      "tutorials.notebook/ResolvedSpecRefs$v1" : {
        "name" : "toResolvedSpecRef",
        "expr" : "{$type: tutorials.notebook/ResolvedSpecRefs$v1, specRefs: (reduce( a = []; x in map(n in notebooks)map(sr in n.specRefs){$type: tutorials.notebook/SpecRefResolver$v1, inputSpecRef: sr, notebookSpecRefs: n.specRefs, workspaceSpecRefs: specRefs}.refineTo( tutorials.notebook/FixedSpecRef$v1 ) ) { a.concat(x) })}"
      }
    }
  }
}
```

A simple test that an empty workspace is valid.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [], specRefs: []}
```

A workspace with spec references.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 2}]}
```

A workspace with a notebook that references one of the specs in the workspace.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 2}]}
```

If a notebook references a non existent spec, then the workspace is not valid with that notebook included.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/FloatingSpecRef$v1, specName: "my/B"}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 2}]}


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"floating ref does not resolve: my/B; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""]
```

The same applies if a notebook references a version of a spec that does not exist.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 2}]}


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"fixed ref not resolve: my/A; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""]
```

Via refinement, we can see the result of resolving all spec references in all notebooks in a workspace. This shows a floating reference being resolved.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/FloatingSpecRef$v1, specName: "my/A"}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}.refineTo( tutorials.notebook/ResolvedSpecRefs$v1 )


//-- result --
{$type: tutorials.notebook/ResolvedSpecRefs$v1, specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}]}
```

This shows a fixed reference being resolved.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}.refineTo( tutorials.notebook/ResolvedSpecRefs$v1 )


//-- result --
{$type: tutorials.notebook/ResolvedSpecRefs$v1, specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}]}
```

A new spec can be created that does not yet exist in the workspace.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 1}], version: 1}], specRefs: []}.refineTo( tutorials.notebook/ResolvedSpecRefs$v1 )


//-- result --
{$type: tutorials.notebook/ResolvedSpecRefs$v1, specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}
```

If a spec with a given name already exists, then a new spec must use the next incremental version of the spec.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 2}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}.refineTo( tutorials.notebook/ResolvedSpecRefs$v1 )


//-- result --
{$type: tutorials.notebook/ResolvedSpecRefs$v1, specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 2}]}
```

If the versions do not match then it is a CAS error.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 1}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}.refineTo( tutorials.notebook/ResolvedSpecRefs$v1 )


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"new versions not in sequence: my/A; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""]
```

The same applies to creating new versions of a spec.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 3}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}.refineTo( tutorials.notebook/ResolvedSpecRefs$v1 )


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"new versions not in sequence: my/A; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""]
```

This shows both a new reference and a fixed reference to that new spec being resolved.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 4}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 4}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}.refineTo( tutorials.notebook/ResolvedSpecRefs$v1 )


//-- result --
{$type: tutorials.notebook/ResolvedSpecRefs$v1, specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 4}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 4}]}
```

A floating reference can point to a new spec created in the notebook.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 4}, {$type: tutorials.notebook/FloatingSpecRef$v1, specName: "my/A"}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}.refineTo( tutorials.notebook/ResolvedSpecRefs$v1 )


//-- result --
{$type: tutorials.notebook/ResolvedSpecRefs$v1, specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 4}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 4}]}
```

Demonstration that invalid fixed references are detected in the presence of new spec references.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 4}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 6}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}.refineTo( tutorials.notebook/ResolvedSpecRefs$v1 )


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"fixed ref not resolve: my/A; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""]
```

Multiple new versions of a spec can be created in a notebook and each of those versions can be referenced via fixed references.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 4}, {$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 5}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 4}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 5}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}.refineTo( tutorials.notebook/ResolvedSpecRefs$v1 )


//-- result --
{$type: tutorials.notebook/ResolvedSpecRefs$v1, specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 4}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 5}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 4}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 5}]}
```

Each of the new versions must match the incremental numbering scheme.

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 4}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}.refineTo( tutorials.notebook/ResolvedSpecRefs$v1 )


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"new versions not in sequence: my/A; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""]
```

```java
{$type: tutorials.notebook/Workspace$v1, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 4}, {$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 6}], version: 1}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}.refineTo( tutorials.notebook/ResolvedSpecRefs$v1 )


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"new versions not in sequence: my/A; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""]
```

One can imagine using the model described above to assess whether a new candidate notebook is valid. i.e. a workspace instance can be created, and the new notebook can be added to it as its sole notebook. If the resulting workspace instance is valid and the tests in the new notebook pass, then the notebook is valid.

Once a notebook has been identified as valid, then it can be "applied" to a workspace. This has the effect of creating any new specs indicated by the notebook.

A second use case is to consider whether some candidate workspace changes are valid in light of a workspace and its notebooks. In this scenario a workspace instance is created which reflects the proposed changes to the workspace. This workspace instance needs to include all of the notebooks that have been registered as regression tests. If the resulting workspace instance is valid and all of the tests in the notebooks pass, then the proposed changes can be made without violating the regression tests. When using a notebook in this mode, the new spec references that existed in the notebook when it was "applied" can be ignored.

A regression test contains the information from a specific point-in-time of a notebook.

```java
{
  "tutorials.notebook/RegressionTest$v1" : {
    "fields" : {
      "notebookName" : "String",
      "notebookVersion" : "Integer",
      "contents" : "String",
      "specRefs" : [ "Vec", "tutorials.notebook/SpecRef$v1" ]
    },
    "constraints" : [ "{expr: (notebookVersion > 0), name: \"positiveVersion\"}", "{expr: ({ 'new-spec-refs' = filter(sr in map(sr in specRefs)sr.refineTo( tutorials.notebook/SpecRef$v1 ))sr.isNew; (0 == 'new-spec-refs'.count()) }), name: \"noNewReferences\"}" ]
  }
}
```

This spec captures the rules governing how notebooks are used to create regression tests.

```java
{
  "tutorials.notebook/RegressionTestMaker$v1" : {
    "fields" : {
      "notebook" : "tutorials.notebook/Notebook$v1",
      "notebookVersion" : "Integer"
    },
    "refines-to" : {
      "tutorials.notebook/RegressionTest$v1" : {
        "name" : "makeTest",
        "expr" : "{$type: tutorials.notebook/RegressionTest$v1, contents: notebook.contents, notebookName: notebook.name, notebookVersion: notebook.version, specRefs: ({ 'spec-refs' = notebook.specRefs; 'new-spec-refs' = filter(sr in map(sr in 'spec-refs')sr.refineTo( tutorials.notebook/SpecRef$v1 ))sr.isNew; (if((notebookVersion == notebook.version)) {'spec-refs'} else {error(\"CAS error creating regression test\")}) })}"
      }
    }
  }
}
```

A trivial example of creating a regression test.

```java
{$type: tutorials.notebook/RegressionTestMaker$v1, notebook: {$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [], version: 1}, notebookVersion: 1}.refineTo( tutorials.notebook/RegressionTest$v1 )


//-- result --
{$type: tutorials.notebook/RegressionTest$v1, contents: "docs", notebookName: "mine", notebookVersion: 1, specRefs: []}
```

The contents and refs of the specific version of the notebook become the contents of the regression test.

```java
{$type: tutorials.notebook/RegressionTestMaker$v1, notebook: {$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/FloatingSpecRef$v1, specName: "my/A"}], version: 1}, notebookVersion: 1}.refineTo( tutorials.notebook/RegressionTest$v1 )


//-- result --
{$type: tutorials.notebook/RegressionTest$v1, contents: "docs", notebookName: "mine", notebookVersion: 1, specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 3}, {$type: tutorials.notebook/FloatingSpecRef$v1, specName: "my/A"}]}
```

However, the notebook cannot contain new spec references.

```java
{$type: tutorials.notebook/RegressionTestMaker$v1, notebook: {$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/NewSpecRef$v1, specName: "my/A", specVersion: 3}], version: 1}, notebookVersion: 1}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/RegressionTestMaker$v1', violates constraints \"tutorials.notebook/RegressionTest$v1/noNewReferences\""]
```

Also if the notebook has been updated, then the creation of the regression test fails. This allows users to detect when notebooks are changed underneath them.

```java
{$type: tutorials.notebook/RegressionTestMaker$v1, notebook: {$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [], version: 2}, notebookVersion: 1}


//-- result --
[:throws "h-err/spec-threw 0-0 : Spec threw error: \"CAS error creating regression test\""]
```

Workspaces can now be extended to include their regression tests.

```java
{
  "tutorials.notebook/Workspace$v2" : {
    "fields" : {
      "specRefs" : [ "Vec", "tutorials.notebook/FixedSpecRef$v1" ],
      "notebooks" : [ "Vec", "tutorials.notebook/Notebook$v1" ],
      "regressionTests" : [ "Vec", "tutorials.notebook/RegressionTest$v1" ]
    },
    "constraints" : [ "{expr: every?(n in notebooks)every?(sr in n.specRefs){$type: tutorials.notebook/SpecRefResolver$v1, inputSpecRef: sr, notebookSpecRefs: n.specRefs, workspaceSpecRefs: specRefs}.refinesTo?( tutorials.notebook/FixedSpecRef$v1 ), name: \"validRefs\"}", "{expr: (#{}.concat(map(t in regressionTests)t.notebookName).count() == regressionTests.count()), name: \"uniqueRegressionTestNotebookNames\"}", "{expr: (#{}.concat(map(n in notebooks)n.name).count() == notebooks.count()), name: \"uniqueNotebookNames\"}" ],
    "refines-to" : {
      "tutorials.notebook/ResolvedSpecRefs$v1" : {
        "name" : "toResolvedSpecRef",
        "expr" : "{$type: tutorials.notebook/ResolvedSpecRefs$v1, specRefs: (reduce( a = []; x in map(n in notebooks)map(sr in n.specRefs){$type: tutorials.notebook/SpecRefResolver$v1, inputSpecRef: sr, notebookSpecRefs: n.specRefs, workspaceSpecRefs: specRefs}.refineTo( tutorials.notebook/FixedSpecRef$v1 ) ) { a.concat(x) })}"
      }
    }
  }
}
```

A workspace can include a regression test.

```java
{$type: tutorials.notebook/Workspace$v2, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}], version: 1}], regressionTests: [{$type: tutorials.notebook/RegressionTest$v1, contents: "docs", notebookName: "mine", notebookVersion: 1, specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}
```

The notebook used as regression test can continue to be changed without affecting the regression tests.

```java
{$type: tutorials.notebook/Workspace$v2, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}], version: 2}], regressionTests: [{$type: tutorials.notebook/RegressionTest$v1, contents: "docs", notebookName: "mine", notebookVersion: 1, specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}
```

However, only a single version of a given notebook can be a regression test at any point in time.

```java
{$type: tutorials.notebook/Workspace$v2, notebooks: [{$type: tutorials.notebook/Notebook$v1, contents: "docs", name: "mine", specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}], version: 2}], regressionTests: [{$type: tutorials.notebook/RegressionTest$v1, contents: "docs", notebookName: "mine", notebookVersion: 1, specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}, {$type: tutorials.notebook/RegressionTest$v1, contents: "docs", notebookName: "mine", notebookVersion: 2, specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}], specRefs: [{$type: tutorials.notebook/FixedSpecRef$v1, specName: "my/A", specVersion: 1}]}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/Workspace$v2', violates constraints \"tutorials.notebook/Workspace$v2/uniqueRegressionTestNotebookNames\""]
```

Validating a notebook in the context of a registry is similar to validating a notebook in the context of the workspace. The difference lies in the fact that different references are in scope, specifically specs that exist in the local workspace but which are not registered locally are omitted from the context. In addition, the notebooks at this stage can only contain fixed and floating spec references, i.e. they cannot contain new spec references.


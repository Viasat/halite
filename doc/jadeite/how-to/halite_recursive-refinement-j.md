<!---
  This markdown file was generated. Do not edit.
  -->

## Recursive refinements

Specs cannot be defined to recursively refine to themselves.

For example, the following spec that refines to itself is not allowed.

```java
{
  "spec/Mirror" : {
    "refines-to" : {
      "spec/Mirror" : {
        "name" : "refine_to_Mirror",
        "expr" : "{$type: spec/Mirror}"
      }
    }
  }
}
```

```java
{$type: spec/Mirror}


//-- result --
[:throws "h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies"]
```

Similarly transitive refinement loops are not allowed. For example, a pair of specs that refine to each other is not allowed.

```java
{
  "spec/Bounce" : {
    "refines-to" : {
      "spec/Back" : {
        "name" : "refine_to_Back",
        "expr" : "{$type: spec/Back}"
      }
    }
  },
  "spec/Back" : {
    "refines-to" : {
      "spec/Bounce" : {
        "name" : "refine_to_Bounce",
        "expr" : "{$type: spec/Bounce}"
      }
    }
  }
}
```

```java
{$type: spec/Bounce}


//-- result --
[:throws "h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies"]
```

It is a bit more subtle, but a cyclical dependency that crosses both a refinement and a composition relationship is also disallowed.

```java
{
  "spec/Car" : {
    "refines-to" : {
      "spec/Garage" : {
        "name" : "refine_to_Garage",
        "expr" : "{$type: spec/Garage, car: {$type: spec/Car}}"
      }
    }
  },
  "spec/Garage" : {
    "spec-vars" : {
      "car" : "spec/Car"
    }
  }
}
```

```java
{$type: spec/Car}


//-- result --
[:throws "h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies"]
```

Diamonds are a bit different than a recursive refinement, but they too are disallowed and produce a similar error.

```java
{
  "spec/Destination" : {
    "spec-vars" : {
      "d" : "Integer"
    }
  },
  "spec/Path1" : {
    "refines-to" : {
      "spec/Destination" : {
        "name" : "refine_to_Destination",
        "expr" : "{$type: spec/Destination, d: 1}"
      }
    }
  },
  "spec/Path2" : {
    "refines-to" : {
      "spec/Destination" : {
        "name" : "refine_to_Destination",
        "expr" : "{$type: spec/Destination, d: 2}"
      }
    }
  },
  "spec/Start" : {
    "refines-to" : {
      "spec/Path1" : {
        "name" : "refine_to_Path1",
        "expr" : "{$type: spec/Path1}"
      },
      "spec/Path2" : {
        "name" : "refine_to_Path2",
        "expr" : "{$type: spec/Path2}"
      }
    }
  }
}
```

```java
{$type: spec/Start}.refineTo( spec/Destination )


//-- result --
[:throws "h-err/refinement-diamond 0-0 : Diamond detected in refinement graph"]
```

Generally, dependency cycles between specs are not allowed. The following spec-map is detected as invalid.

```java
{
  "spec/Self" : {
    "constraints" : [ "{expr: (1 == [{$type: spec/Self}].count()), name: \"example\"}" ]
  }
}


//-- result --
[:throws "h-err/spec-cycle 0-0 : Cycle detected in spec dependencies" :h-err/spec-cycle]
```

Although this error can be detected in advance, if an attempt is made to use the spec, then a similar runtime error is produced.

```java
{$type: spec/Self}


//-- result --
[:throws "h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies"]
```


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
[:throws "h-err/refinement-loop 0-0 : Loop detected in refinement graph"]
```

Similarly transitive refinement loops are not allowed. For example, a pair of specs that refine to each other is not allowed.

```java
{
  "spec/A" : {
    "refines-to" : {
      "spec/B" : {
        "name" : "refine_to_B",
        "expr" : "{$type: spec/B}"
      }
    }
  },
  "spec/B" : {
    "refines-to" : {
      "spec/A" : {
        "name" : "refine_to_A",
        "expr" : "{$type: spec/A}"
      }
    }
  }
}
```

```java
{$type: spec/A}


//-- result --
[:throws "h-err/refinement-loop 0-0 : Loop detected in refinement graph"]
```

It is a bit more subtle, but a cyclical dependency that crosses both a refinement and a composition relationship is also disallowed.

```java
{
  "spec/A" : {
    "refines-to" : {
      "spec/B" : {
        "name" : "refine_to_B",
        "expr" : "{$type: spec/B, a: {$type: spec/A}}"
      }
    }
  },
  "spec/B" : {
    "spec-vars" : {
      "a" : "spec/A"
    }
  }
}
```

```java
{$type: spec/A}


//-- result --
[:throws "h-err/refinement-loop 0-0 : Loop detected in refinement graph"]
```

Diamonds are a bit different than a recursive refinement, but they too are disallowed and produce a similar error.

```java
{
  "spec/A" : {
    "spec-vars" : {
      "a" : "Integer"
    }
  },
  "spec/B" : {
    "refines-to" : {
      "spec/A" : {
        "name" : "refine_to_A",
        "expr" : "{$type: spec/A, a: 1}"
      }
    }
  },
  "spec/C" : {
    "refines-to" : {
      "spec/A" : {
        "name" : "refine_to_A",
        "expr" : "{$type: spec/A, a: 2}"
      }
    }
  },
  "spec/D" : {
    "refines-to" : {
      "spec/B" : {
        "name" : "refine_to_B",
        "expr" : "{$type: spec/B}"
      },
      "spec/C" : {
        "name" : "refine_to_C",
        "expr" : "{$type: spec/C}"
      }
    }
  }
}
```

```java
{$type: spec/D}.refineTo( spec/A )


//-- result --
[:throws "h-err/refinement-diamond 0-0 : Diamond detected in refinement graph"]
```


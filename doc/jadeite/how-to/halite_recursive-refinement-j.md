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


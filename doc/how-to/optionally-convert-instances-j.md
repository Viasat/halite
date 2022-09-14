## Optionally Converting Instances Between Specs

Refinement expressions can include logic to optionally convert an instance.

In the following example, the refinement expression determines whether to convert an instance based on the value of 'b'.

```java
{
  "spec/A$v1" : {
    "spec-vars" : {
      "b" : "Integer"
    },
    "refines-to" : {
      "spec/X$v1" : {
        "name" : "refine_to_X",
        "expr" : [ "when", [ ">", "b", 10 ], {
          "$type" : "spec/X$v1",
          "y" : "b"
        } ]
      }
    }
  },
  "spec/X$v1" : {
    "spec-vars" : {
      "y" : "Integer"
    }
  }
}
```

In this example, the refinement applies.

```java
{$type: spec/A$v1, b: 20}.refineTo( spec/X$v1 )


//-- result --
{$type: spec/X$v1, y: 20}
```

In this example, the refinement does not apply

```java
{$type: spec/A$v1, b: 5}.refineTo( spec/X$v1 )


//-- result --
[:throws "h-err/no-refinement-path 0-0 : No active refinement path from 'spec/A$v1' to 'spec/X$v1'"]
```

A refinement path can be probed to determine if it exists and if it applies to a given instance.

```java
{$type: spec/A$v1, b: 20}.refinesTo?( spec/X$v1 )


//-- result --
true
```

```java
{$type: spec/A$v1, b: 5}.refinesTo?( spec/X$v1 )


//-- result --
false
```

#### Basic elements:

[`instance`](jadeite-basic-syntax-reference.md#instance)


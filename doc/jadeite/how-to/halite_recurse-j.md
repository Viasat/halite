<!---
  This markdown file was generated. Do not edit.
  -->

## Recursive instances

Specs can be defined to be recursive.

```java
{
  "spec/Cell" : {
    "fields" : {
      "value" : "Integer",
      "next" : [ "Maybe", "spec/Cell" ]
    }
  }
}
```

```java
{$type: spec/Cell, value: 10}
```

```java
{$type: spec/Cell, next: {$type: spec/Cell, value: 11}, value: 10}
```


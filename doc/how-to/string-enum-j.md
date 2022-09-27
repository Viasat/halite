<!---
  This markdown file was generated. Do not edit.
  -->

## String as enumeration

How to model an enumeration as a string

Say we want to model a shirt size and the valid values are "small", "medium", and "large". We can start by modeling the size as a string.

```java
{
  "spec/Shirt$v1" : {
    "spec-vars" : {
      "size" : "String"
    }
  }
}
```

This is a start, but it allows invalid size values.

```java
{$type: spec/Shirt$v1, size: "XL"}


//-- result --
{$type: spec/Shirt$v1, size: "XL"}
```

So we can add a constraint to limit the values to what we expect.

```java
{
  "spec/Shirt$v2" : {
    "spec-vars" : {
      "size" : "String"
    },
    "constraints" : [ [ "size_constraint", "#{\"large\", \"medium\", \"small\"}.contains?(size)" ] ]
  }
}
```

Now the shirt with the invalid size cannot be constructed.

```java
{$type: spec/Shirt$v2, size: "XL"}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Shirt$v2', violates constraints size_constraint"]
```

But a shirt with a valid size can be constructed.

```java
{$type: spec/Shirt$v2, size: "medium"}


//-- result --
{$type: spec/Shirt$v2, size: "medium"}
```

#### Basic elements:

[`instance`](../jadeite-basic-syntax-reference.md#instance)

#### How tos:

* [spec-variables](spec-variables.md)



<!---
  This markdown file was generated. Do not edit.
  -->

## What is an abstract spec?

An abstract spec defines instances which cannot be used in the construction of other instances.

Say we have an abstract concept of squareness.

```java
{
  "spec/Square" : {
    "abstract?" : true,
    "fields" : {
      "width" : "Integer",
      "height" : "Integer"
    }
  }
}
```

The spec can be instantiated in a standalone fashion.

```java
{$type: spec/Square, height: 5, width: 5}
```

However, this spec cannot be instantiated in the context of another instance. So consider the following two specs, where a concrete spec uses an abstract spec in composition.

```java
{
  "spec/Square" : {
    "abstract?" : true,
    "fields" : {
      "width" : "Integer",
      "height" : "Integer"
    }
  },
  "spec/Painting" : {
    "fields" : {
      "square" : "spec/Square",
      "painter" : "String"
    }
  }
}
```

The instance of the abstract spec cannot be used in the construction of the painting instance.

```java
{$type: spec/Painting, painter: "van Gogh", square: {$type: spec/Square, height: 5, width: 5}}


//-- result --
[:throws "h-err/instance-threw 0-0 : Instance threw error: \"Instance cannot contain abstract value\""]
```

To create an instance of the composite painting spec, we need to define an additional spec which refines to the abstract spec, square.

```java
{
  "spec/Square" : {
    "abstract?" : true,
    "fields" : {
      "width" : "Integer",
      "height" : "Integer"
    }
  },
  "spec/Painting" : {
    "fields" : {
      "square" : "spec/Square",
      "painter" : "String"
    }
  },
  "spec/Canvas" : {
    "fields" : {
      "size" : "String"
    },
    "refines-to" : {
      "spec/Square" : {
        "name" : "refine_to_square",
        "expr" : "(if((\"small\" == size)) {{$type: spec/Square, height: 5, width: 5}} else {{$type: spec/Square, height: 10, width: 10}})"
      }
    }
  }
}
```

Now we can instantiate a painting using an instance of the concrete canvas spec.

```java
{$type: spec/Painting, painter: "van Gogh", square: {$type: spec/Canvas, size: "large"}}


//-- result --
{$type: spec/Painting, painter: "van Gogh", square: {$type: spec/Canvas, size: "large"}}
```

We can determine the size of the square.

```java
({ painting = {$type: spec/Painting, painter: "van Gogh", square: {$type: spec/Canvas, size: "large"}}; painting.square.refineTo( spec/Square ).width })


//-- result --
10
```

An abstract spec is a spec that can be used to define some constraints on the value in a composite spec without indicating precisely what type of instance is used in the composition. In this example, the painting spec is defined to include a square without any reference to the canvas.

Consider another spec context, where an alternate spec is defined that refines to square.

```java
{
  "spec/Square" : {
    "abstract?" : true,
    "fields" : {
      "width" : "Integer",
      "height" : "Integer"
    }
  },
  "spec/Painting" : {
    "fields" : {
      "square" : "spec/Square",
      "painter" : "String"
    }
  },
  "spec/Wall" : {
    "refines-to" : {
      "spec/Square" : {
        "name" : "refine_to_square",
        "expr" : "{$type: spec/Square, height: 100, width: 100}"
      }
    }
  }
}
```

In this example, the exact same painting spec is used, but now a new spec is used to provide the square abstraction.

```java
{$type: spec/Painting, painter: "van Gogh", square: {$type: spec/Wall}}


//-- result --
{$type: spec/Painting, painter: "van Gogh", square: {$type: spec/Wall}}
```

Once again, we can use the same code as before to retrieve the size of the square for this painting.

```java
({ painting = {$type: spec/Painting, painter: "van Gogh", square: {$type: spec/Wall}}; painting.square.refineTo( spec/Square ).width })


//-- result --
100
```

So the abstract spec allows us to write code that composes and uses instances without knowing the specific type of the instances at the time that we write the code.

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### Operator reference:

* [`refineTo`](../halite_full-reference-j.md#refineTo)


#### Explanations:

* [refinement-terminology](../explanation/halite_refinement-terminology-j.md)



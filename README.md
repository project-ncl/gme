[![Build Status (Travis CI)](https://travis-ci.org/project-ncl/gradle-manipulator.svg?branch=master)](https://travis-ci.org/project-ncl/gradle-manipulator.svg?branch=master)

# Source code for Gradle manipulation plugins

Both plugins work in conjunction and therefore the same version is required for each. The analyzer plugin will inject a reference
 to the manipulation plugin. It requires Gradle 5.6.x - 6.x to _build_ and can _run_ with Gradle 4.10.x - 6.x.

## Analyzer

The `analyzer` directory contains the gradle plugin that generates metadata information about aligned dependencies and the project version.

## Manipulation

The `manipulation` directory contains the gradle plugin that uses the metadata information generated by the `alignment` plugin and
modifies the project to use those dependencies and project version.


## Testing on a real project

The plugins can be tested on real projects like so:

An init script is required. If a development version is being used it is processed during the build into
`analyzer/build/resources/main/analyzer-init.gradle`.  If a released version is being used it is deployed as `analyzer-<version>-init.gradle`
and may be found in Maven Central i.e. https://repo1.maven.org/maven2/org/jboss/gm/analyzer/2.4/analyzer-2.4-init.gradle

Now by executing the following command:

```
./gradlew --info --init-script analyzer-init.gradle generateAlignmentMetadata -DrestURL=http://some.da.server
```

you should get the `manipulation.json` file in the root of the project. For detailed documentation on the parameters please see
[here](https://project-ncl.github.io/gradle-manipulator/).

## Contributions

Contributions are more than welcome! Before contributing to the project, please read [this](https://github.com/project-ncl/gradle-manipulator/blob/master/CONTRIBUTING.md).

## Documentation

Documentation for the project can be found [here](https://project-ncl.github.io/gradle-manipulator/)

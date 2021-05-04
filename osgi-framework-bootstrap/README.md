# OSGi Framework Bootstrap

This module provides the functionalities to the **Common App** plugin to build
the bootable jar to distribute applications.

See the `README.md` file in the `buildSrc` module to know how to build libraries and
applications part of a bootable jar.

See KDoc in source code for additional info.

## Tests

The `framework-app-tester` module applies the **Common App** plugin to build a test application (used in future tests),
a test OSGi bundle JAR, the `system_bundles` and `system_packages_extra` files to use to test the 
`net.corda.osgi.framework` package.

The Gradle task `test` in this module is overridden to build first the OSGi bundle from the `framework-app-tester`
module, and to compile the `system_bundles` list.
The `system_packages_extra` is provided in the `test/resources` directory of the module.
These files are copied in the locations...

```
<buildDir>
\___ resources
     +--- test
     \___ bundles
          +--- framework-app-tester-<version>.jar
          +___ system_bundles
          \___ system_packages_extra
```

The artifacts children of the `<buildDir>/resources/test` are in the class-path at test time,
hence accessible from the test code.

**IMPORTANT! Run the `test` task to execute unit tests for this module.**

*WARNING! To run tests from IDE, configure*

`Settings -> Build, Execution, Deployment -> Build Tools -> Gradle`

*and set in the pane*

`Gradle Projects -> Build and run -> Run tests using: IntelliJ IDEA`

*and run the `test` task at least once after `clean` to assure the test artifacts are generated before
tests run; then tests can be executed directly from the IDE.*
 
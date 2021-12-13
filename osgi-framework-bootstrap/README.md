# OSGi Framework Bootstrap

This module provides the functionalities to the **Common App** plugin to build
the bootable jar to distribute applications.

See the `README.md` file in the `buildSrc` module to know how to build libraries and
applications part of a bootable jar.

See KDoc in source code for additional info.

## Tests

*NOTE! To run tests from IDE, configure*

`Settings -> Build, Execution, Deployment -> Build Tools -> Gradle`

*and set in the pane*

`Gradle Projects -> Build and run -> Run tests using: IntelliJ IDEA`

*then tests can be executed directly from the IDE.*

## Logging

By default, all logging is at info level. The logs are sent to stdout, to support containerised deployments.

The default Log4j config is located at `src/main/resources/log4j2.xml`. The
`-Dlog4j.configurationFile=path/to/file.xml` system property can be used to override this config.

The `-Dlog4j.debug` system property can also be used to log debug-level and trace-level messages to the console.
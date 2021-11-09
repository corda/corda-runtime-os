These commands are run from `corda-runtime-os/applications/examples/serialization-amqp/type-evolution-poc`.

Build CPK:

`../../../../gradlew build`

Build application:

`../../../../gradlew appJar`

Resulting JAR file is: `build/bin/corda-type-evolution-poc-5.0.0.0-SNAPSHOT.jar`

Run: `java -jar build/bin/corda-type-evolution-poc-5.0.0.0-SNAPSHOT.jar tmp/ evolution-example-cpk/build/libs/evolution-example-workflows-5.0.0.0-SNAPSHOT-cordapp.cpk`

Parameters:
1. CPK temp dir
2. CPK to load


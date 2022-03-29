The following commands are run from `corda-runtime-os/applications/examples/amqp-custom-serializer-poc`.

Build CPKs:

`../../../gradlew build`

Build application:

`../../../gradlew appJar`

Resulting JAR file is: `build/bin/corda-amqp-custom-serializer-poc-5.0.0.0-SNAPSHOT.jar`

Run: `java -jar build/bin/corda-amqp-custom-serializer-poc-5.0.0.0-SNAPSHOT.jar tmp/ cpk-a/build/libs/custom-serializer-example-workflows-a-5.0.0.0-SNAPSHOT-cordapp.cpk cpk-b/build/libs/custom-serializer-example-workflows-b-5.0.0.0-SNAPSHOT-cordapp.cpk`

Parameters:
1. CPK temp dir
2. CPK A to load
2. CPK B to load

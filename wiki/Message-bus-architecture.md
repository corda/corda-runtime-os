
The message bus architecture is primarily designed to separate the logic of the message patterns (ie. subscriptions/publishers) from the underlying technology that delivers the messages themselves (either Kafka or a database).
There is a nice diagram of the [architecture on the design repo](https://github.com/corda/platform-eng-design/blob/master/core/corda-5/corda-5.1/flow-worker/messaging/CORDA-2899-message-pattern-structure.md).

## Using the different message buses

Each bus is designed to be bundled into an application separately. You must specify in your build which bus to use.

### Using the Kafka message bus

For the Kafka message bus, add the following to your `build.gradle`:

```groovy
    implementation project(":components:kafka-topic-admin")

    runtimeOnly project(":libs:messaging:kafka-topic-admin-impl")
    runtimeOnly project(":libs:messaging:kafka-message-bus-impl")
```

### Using the DB message bus

Unfortunately, there is a lot of boiler plate in getting the DB message bus working. It is basically just getting the correct dependencies included in your `build.gradle`. Add the following to your `build.gradle` for the DB message bus:

```groovy
    runtimeOnly project(":libs:messaging:messaging-impl")
    runtimeOnly project(":libs:messaging:db-message-bus-impl")
    runtimeOnly project(":libs:schema-registry:schema-registry-impl")

    runtimeOnly "org.apache.aries.spifly:org.apache.aries.spifly.dynamic.bundle:$ariesDynamicBundleVersion"
    runtimeOnly "com.sun.activation:javax.activation:$activationVersion"
    runtimeOnly "org.hsqldb:hsqldb:$hsqldbVersion"
    runtimeOnly "org.postgresql:postgresql:$postgresDriverVersion"
```

### "In-memory" testing using the DB message bus

When testing libraries and components it is likely that you will want to forego Kafka for messaging and, instead, opt for an in-memory message bus. For this we have decided to use the DB message bus, using the HSQL in-memory database. This is very similar to the changes needed for using the `db-message-bus` for actual database messaging. The main difference is that you must add some dependencies and adjust your test and `test.bndrun` file.

Add the following to your `build.gradle` for testing on the DB message bus:

```groovy
    integrationTestImplementation project(":testing:db-message-bus-testkit")

    integrationTestRuntimeOnly project(":libs:messaging:messaging-impl")
    integrationTestRuntimeOnly project(":libs:messaging:db-message-bus-impl")
    integrationTestRuntimeOnly project(":libs:schema-registry:schema-registry-impl")

    integrationTestRuntimeOnly "org.apache.aries.spifly:org.apache.aries.spifly.dynamic.bundle:$ariesDynamicBundleVersion"
    integrationTestRuntimeOnly "com.sun.activation:javax.activation:$activationVersion"
    integrationTestRuntimeOnly "org.hsqldb:hsqldb:$hsqldbVersion"
```

Here are the things you need to add to your `test.bndrun` for testing on the DB message bus:

```
    bnd.identity;id='net.corda.messaging-impl',\
    bnd.identity;id='net.corda.db-message-bus-impl',\
    bnd.identity;id='net.corda.db-orm-impl',\
    bnd.identity;id='net.corda.schema-registry-impl',\
    bnd.identity;id='net.bytebuddy.byte-buddy',\
    bnd.identity;id='org.hsqldb.hsqldb',\
```

And, finally, you must add `DBSetup::class` to your OSGi test class as a `@ServiceExtension`:

```kotlin
 @ExtendWith(ServiceExtension::class, DBSetup::class)
class SomeOSGiIntegrationTest {
```

This allows the in-memory database to be correctly set up with the necessary tables for messaging. **Note:** This automatic database setup only works for HSQL. PostgreSql is not supported.
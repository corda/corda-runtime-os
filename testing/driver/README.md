Corda Driver
------------

The Corda Driver is an integration testing framework for executing and debugging flows inside CPBs.

The Driver launches an embedded OSGi framework containing a subset of Corda's components that includes
the Flow Messaging layer and Quasar, along with the sandbox components and a HSQLDB database.

## Gradle Configuration

Apply the `corda.driver-tests` convention to your Gradle project:
```gradle
plugins {
    id 'corda.driver-tests'
}
```

This plugin creates Gradle configurations for the CPBs that you wish to test. (These CPBs are
expected to have been created by the `net.corda.plugins.cordapp-cpb2` Gradle plugin.)

```gradle
dependencies {
    testCpks project(':testing:cpbs:mandelbrot')
}
```

This adds a project's CPK to the `testImplementation` configuration, and its corresponding CPB
to the `testRuntimeOnly` configuration, with the `cordapp-cpb2` plugin handling any transitive
CPK dependencies.

## Writing JUnit Tests

Add the Corda Driver to a JUnit test by registering it as a JUnit extension, e.g. to use the same
framework for all tests (c.f. `@BeforeAll`):
```kotlin
@RegisterExtension
private val driver = DriverNodes(alice, bob).forAllTests()
```

or to create a new framework for each test (c.f. `@BeforeEach`):
```kotlin
@RegisterExtension
private val driver = DriverNodes(alice, bob, charlie).forEachTest()
```

where `alice`, `bob` and `charlie` are X500 identities, provided as instances of `MemberX500Name`. 

JUnit will register this extension before invoking any of the test's own `@BeforeAll` or
`@BeforeEach` methods. This will define the Membership Group and launch an OSGi
framework without any virtual nodes. To create your virtual nodes, you must execute `startNodes()`:
```kotlin
@BeforeAll
fun setup() {
    driver.run { dsl ->
        dsl.startNodes(setOf(alice, bob)).filter { vNode ->
            vNode.cpiIdentifier.name == "ledger-utxo-demo-app"
        }.associateByTo(utxoLedger) { vNode ->
            vNode.holdingIdentity.x500Name
        }
        assertThat(utxoLedger).hasSize(2)
    }
}
```

`startNodes()` will create one `VirtualNodeInfo` object for each CPB on the classpath, and
for each `MemberX500Name` in its parameter list. These `MemberX500Name` objects must all
belong to the Membership Group defined when the Driver extension was registered.

(NOTE: The ultimate goal will be to _choose_ which CPBs are loaded for a given X500 name. But
for now at least, everyone gets everything.)

You can initiate a flow request for a given `VirtualNodeInfo` using the `runFlow()` function:
```java
@FunctionalInterface
public interface RunFlow {
    @Nullable
    String runFlow(
        @NotNull VirtualNodeInfo virtualNodeInfo,
        @NotNull String flowClassName,
        @NotNull String flowStartArgs
    );
}
```
where `flowClassName` is the fully qualified name of a flow class that extends `ClientStartableFlow`,
and `flowStartArgs` is a `String` containing a JSON document. For example:
```kotlin
val inputResult = driver.let { dsl ->
    dsl.runFlow(utxoLedger[alice] ?: fail("Missing vNode for Alice"), UtxoDemoFlow::class.java) {
        val request = UtxoDemoFlow.InputMessage(
            input = TEST_INPUT,
            members = listOf(bob.toString(), charlie.toString()),
            notary = notary.toString()
        )
        jsonMapper.writeValueAsString(request)
    }
} ?: fail("inputResult must not be null")
```

The content and format of the returned `String` is determined by the flow itself.

If the flow fails to execute then the Driver will throw `net.corda.testing.driver.FlowErrorException`,
or possibly `net.corda.testing.driver.FlowFatalException` if the flow is `KILLED`.

## Running the Driver
The `corda.driver-tests` Gradle convention plugin automatically configures the Gradle project's
`Test` tasks such that:
- The JVM is configured with sufficient `--add-opens` JVM parameters to allow the Driver
to run correctly on Java 17+.
- The `java.io.tmpdir` system property is set to the project's `build/` directory.
- Quasar's `co.paralleluniverse.fibers.verifyInstrumentation` system property is set to `true`.

The Driver will then create these subdirectories of `build/`:
- `corda-driver-<number>`, as the OSGi framework's internal working directory.
- `corda-driver-cache`, as the Driver's own cache directory for CPKs.
- `quasar-cache`, as Quasar's cache directory for instrumented Java byte-code.

The `corda-driver-cache` and `quasar-cache` directories are shared by
all instances of the Driver's OSGi framework, and may be accessed concurrently.

# Driver Architecture

## Overview
The Driver creates an embedded OSGi framework that contains Corda's Flow Messaging layer,
but without Corda's Lifecycle layer, REST layer, or Kafka. It also replaces Corda's sandbox
components with its own so that it can load CPBs as resources from the classpath.

CorDapp developers are only expected to interact with the Driver via JUnit and the `DriverDSL`:
```java
@DoNotImplement
public interface DriverDSL {
    @NotNull
    List<VirtualNodeInfo> startNodes(@NotNull Set<MemberX500Name> memberNames);

    @Nullable
    String runFlow(
        @NotNull VirtualNodeInfo virtualNodeInfo,
        @NotNull Class<?> flowClass,
        @NotNull ThrowingSupplier<String> flowArgMapper
    );
}
```
It should _not_ be necessary for CorDapp developers to understand OSGi in order to use the Driver.

The Driver's JUnit API is written in pure Java, partly to make it easier for CorDapp developers
to write Driver tests in Java, but also to support writing Driver tests using a different version
of Kotlin to the one that Corda itself uses.

JUnit interacts with the embedded OSGi framework via the
`net.corda.testing.driver.EmbeddedNodeService` and `net.corda.testing.driver.RunFlow`
interfaces, which are restricted to using basic Java types and Corda API types (which
in turn are also written in pure Java).

## The Simulated Corda Network

The Driver's Corda network consists of the members declared via `DriverNodes(...)` plus any
notaries. Each member is assigned its own key pair, which by default is generated using the
`CORDA.ECDSA.SECP256R1` key scheme.

A virtual node is uniquely identified by its `HoldingIdentity(X500Name, groupId)`.

A flow may only communicate with another virtual node whose `HoldingIdentity` has a matching
`groupId` value.

The Driver assigns a unique `groupId` to each distinct CPB that it loads from the classpath.

When a simulated network also contains a notary, the Driver allocates a new virtual node with
that notary's X500 name to each of the network's distinct `groupId`s. This notary virtual node
always maps to the `notary-plugin-non-validating-server` CPB.

A simulated network can contain more than one notary X500 name.

## The Embedded OSGi Framework

The Driver controls and configures its components using OSGi's standard "Service Component Runtime"
(SCR) and "Configuration Admin" components.

It constructs the embedded OSGi framework by scanning its classpath for `META-INF/MANIFEST.MF`
resources, and identifying those which belong to bundles, CPKs and CPBs.

Non-bundles and CPK resources are always ignored. Any CPB resources are recorded and loaded later
via the `EmbeddedNodeService`.

### The System Bundle
Passing data into and out of our embedded OSGi framework requires that our data's
classes exist both inside and outside our framework. These classes must therefore
belong to the framework's system bundle.

We define our system bundle also to include these bundles:
- All Corda API bundles, which we identify by a `Corda-Api` tag in their manifest.
- Avro
- Bouncy Castle
- `slf4j.api`
- `jcl.over.slf4j`, which is SLF4J's implementation of the `commons-logging` API
- `javax.persistence-api`
- `org.osgi.service.component`
- `org.osgi.service.jdbc`
- `org.osgi.service.log`
- `org.osgi.util.function`
- `org.osgi.util.promise`

Our system bundle also needs to contain the `net.corda.testing.driver.node` package,
which is defined by the Driver's own `net.corda.driver` bundle.

### Excluded Bundles
We explicitly exclude these bundles from being installed into the OSGi framework:
- Any individual SLF4J bundles beyond those already included in the system bundle.
- Apache's implementation of the `commons-logging` API.
- Apache Felix
- Bnd
- JUnit
- TestNG
- AssertJ
- Mockito

### `EmbdeddedNodeService`
This is the "main" component inside the framework that JUnit interacts with, and is
declared to be an "immediate" component. This means that the framework will activate
it automatically as soon as it installs the `corda-driver-engine` bundle. However,
the Driver still needs to invoke its
```java
interface EmbeddedNodeService {
    void configure(@NotNull Duration timeout);
}
```
method to deactivate any Corda components which could conflict with the Driver's
replacement components, and then activate the `VirtualNodeService` component.

These replacement services are:
- `CpiInfoReadService`
- `CpkReadService`
- `DatabaseTypeProvider`
- `DbConnectionManager`
- `GroupPolicyProvider`
- `MembershipGroupReaderProvider`
- `SandboxGroupContextComponent`
- `WrappingRepository`

The Driver's own version of these components are all marked as `corda.driver:Boolean=true`,
and so `EmbeddedNodeServiceImpl` can identify all versions without this property and then
use the SCR to deactivate them.

### `VirtualNodeService`
This component enables the Driver to create a sandbox from a loaded CPB.

### `VirtualNodeLoader`
This component loads CPBs as classpath resources, and provides each distinct resource
with its own unique `groupId` value.

### `RunFlowImpl`
This component implements a single-threaded version of the `FlowEvent` messaging loop.

The flow to be executed must extend `ClientStartableFlow` and accept a `String` containing
a JSON document as a parameter.

The component will repeatedly handle flow events until one of the following occurs:
- It receives a `FlowStatus` event with an `initiatorType` of `RPC`.
- It has no more `FlowEvent`s left to process.
- The configured `PROCESSOR_TIMEOUT` time limit is exceeded. 

The following event topic are mapped to instances of `ExternalProcessor:
- `crypto.ops.flow` -> `CryptoProcessor`
- `persistence.entity.processor` -> `EntityProcessor`
- `persistence.ledger.processor` -> `LedgerProcessor`
- `uniqueness.check` -> `UniquenessProcessor`
- `verification.ledger.processor` -> `VerifyProcessor`

These `ExternalProcessor` components are only instantiated if / when they are needed.

#### CryptoProcessor
Corda's crypto handling requires a database with a schema, which would potentially make it
fairly expensive for the Driver to initialise. The Driver therefore currently implements
its own flow message handling that does not need a database.

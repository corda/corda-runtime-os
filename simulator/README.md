# Corda 5 Simulator

Corda 5 Simulator is a lightweight testing / demo tool that simulates a Corda 5 network, enabling you to run Cordapps,
demonstrate realistic behaviour and get feedback on how they are likely to behave with a real Corda network.

Simulator does not verify identity, check permissions, keep anything secure or encrypted, suspend / restart
flows or handle more than one version of any Flow.  It is intended only for low-level testing (meaning, providing
quick feedback and documenting examples of how things work) or demoing CorDapps. For full testing, use a real or
production-like implementation of Corda.

## Setting up dependencies

You will need to set up the following dependencies in your build file, assuming you are using this for testing:

```
      testImplementation "net.corda:corda-simulator-api:$simulatorVersion"
      testRuntimeOnly "net.corda:corda-simulator-runtime:$simulatorVersion"
```

Do not use the runtime libraries as an implementation dependency, as your code may fail to compile or run
when Simulator is updated. The API is much more stable.

## How to run Simulator

The main class for starting your CorDapps is `net.corda.simulator.Simulator`. "Uploading" your flow for a given party 
will create a simulated "virtual node" which can then be invoked using the initiating flow class (in a real Corda
network this would be done using the `CPI_HASH`).

```kotlin
  val simulator = Simulator()
  val member = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")
  val node = simulator.createVirtualNode(member, HelloFlow::class.java)

  val response = node.callFlow(
      RequestData.create("r1", HelloFlow::class.java.name, "{ \"name\" : \"CordaDev\" }")
  )
```

Simulator will wire up your flow with lightweight versions of the same injected services that you'd get with
the real Corda, enabling your flows to communicate with each other, persist data (currently to an in-memory database)
and "sign" data (see below).

To release resources used by Simulator, including any database connections, call

```kotlin
  simulator.close()
```

Note that Simulator runs the node and flow setup synchronously. It is advised that all node and flow setup should 
be done synchronously on the same thread, and not use asynchronous code.

## Logging

Simulator uses SLF4j for its logging. To turn logging on, either add a dependency to a logging framework bridge of your
choice, for instance:

    testImplementation 'org.apache.logging.log4j:log4j-slf4j-impl:2.19.0'

or turn on SLF4J's simple logging:

    testImplementation `org.slf4j:slf4j-simple:2.0.4`

## Configuration

Simulator configuration can be set using the `SimulatorConfigurationBuilder`. You can configure:
- the clock (defaults to system default clock)
- timeouts for flows (defaults to 1 minute)
- polling interval (defaults to 100 ms)
- service overrides

### Time-based Configuration

The default timeout should be suitable for most tests. For demos, showcasing proofs of concept etc. you may want
to make it longer.

The polling interval is used to check for any exceptions thrown by responder flows.

```kotlin
val config = SimulatorConfigurationBuilder.create()
  .withTimeout(Duration.ofMinutes(2))
  .withPollInterval(Duration.ofMillis(50))
  .build()
val simulator = Simulator(config)
```

### Service Overrides

Simulator provides implementations of various services which Corda injects into flows. You can override or decorate
these by providing your own `ServiceOverrideBuilder`; a SAM interface which takes three parameters:

- the member whose flow is being constructed,
- the flowClass for which the service is being constructed,
- Simulator's implementation of the service.

If Simulator doesn't support an implementation of the given service (for instance, if using a version that is out of
sync with your version of Corda) then the service provided by Simulator will be null. You can still provide your own
implementation in this instance.

```kotlin
val serviceBuilder = ServiceOverrideBuilder<JsonMarshallingService> {
    member, flowClass, service -> MyJsonMarshallingService(member, flowClass, service)
}
val config = SimulatorConfigurationBuilder.create()
  .withServiceOverride(JsonMarshallingService::class.java, serviceBuilder)
  .build()
```

### Custom Serializer
Simulator provides the ability to register custom serializers. You could register your own
serializers using Simulator configuration as shown below.

```kotlin
val config = SimulatorConfigurationBuilder.create()
  .withCustomSerializer(myCustomSerializer)
  .build()
```

You could also register custom JSON serializer/ deserializer as shown below.

```kotlin
val config = SimulatorConfigurationBuilder.create()
  .withCustomJsonSerializer(myCustomJsonSerializer, MyType::class.java)
  .withCustomJsonDeserializer(myCustomJsonDeserializer, MyType::class.java)
  .build()
```


## RequestData

Corda normally takes requests via its API in the form of JSON-formatted strings, which are converted
by Corda into an `RestRequestBody` interface. This is represented in Simulator by a `RequestData` factory,
which allows Simulator to construct an `RestRequestBody` when the flow is called. There are three different construction
methods available:

- A JSON-formatted string, as you would submit with `curl`:

```kotlin
val jsonInput = """
{
  "httpStartFlow": {
    "clientRequestId": "r1",
    "flowClassName": "${CalculatorFlow::class.java.name}",
    "requestData":  "{ \"a\" : 6, \"b\" : 7 }"
  }
}
""".trimIndent()
val requestBody = RequestData.create(jsonInput)
```

- A three-part constructor with the request and flow classname separately, as you would submit through
  Swagger UI:

```kotlin
val requestBody = RequestData.create(
    "r1", 
    "${CalculatorFlow::class.java.name}",
    "{ \"a\" : 6, \"b\" : 7 }"
)
```

- A three-part constructor that is strongly typed:

```kotlin
val request = RequestData.create(
    "r1", 
    CalculatorFlow::class.java, 
    InputMessage(6, 7)
)
```

## Instance vs Class upload

Simulator has two methods of creating nodes with flows:
- via a flow class, which will be constructed when a response flow is initialized.
- via a flow instance, which must be uploaded against a protocol.

Uploading an instance allows flows to be constructed containing other mocks, injected logic, etc. It also
allows mocks to be used in place of a real flow. For instance, using Mockito:

```kotlin
val responder = mock<ResponderFlow>()
whenever(responder.call(any())).then {
    val session = it.getArgument<FlowSession>(0)
    session.receive<RollCallRequest>()
    session.send(RollCallResponse(""))
}

val node = simulator.createVirtualNode(
    MemberX500Name.parse(studentId),
    "roll-call",
    responder
)
```

Note that uploading an instance of a flow bypasses all the checks that Simulator would normally carry out on
the flow class. All services will be injected into instance flows as normal.

## Key Management and Signing

In real Corda, an endpoint is available for generating keys with different schemes and different HSM categories.
Currently, only signing with ledger keys is supported. These can be accessed through `MemberInfo` available in 
the `MemberLookup` service.

In Simulator, keys can be generated via a method on the virtual node:

```kotlin
val publicKey = node.generateKey("my-alias", HsmCategory.LEDGER, "CORDA.ECDSA.SECP256R1")
```

Simulator's `SigningService` and other crypto services that sign mimic the real things by wrapping the bytes provided
in a readable JSON wrapper, using the key, alias, HSM category and signature scheme.

```json
{
  "clearData":"<clear data bytes>",
  "encodedKey":"<PEM encoded public key>",
  "signatureSpecName":"<signature spec name>",
  "keyParameters":{"alias":"<alias>","hsmCategory":"LEDGER","scheme":"<scheme>"}
}
```

The equivalent `DigitalVerificationService` simply looks to see if the clear data, signature spec and key are a match.

Note that as with real Corda, all public keys are accessible through `MemberInfo`, and that even though Simulator does 
not perform any actual crypto, a member cannot use another member's key in signing.

Note also that Simulator does not check to see if any given scheme is supported, and will only
ever generate an ECDSA key, regardless of parameters. To verify that your chosen key scheme and signature spec
are supported and work together, test using a real Corda deployment.

> **⚠ Warning**
> 
> Simulator never actually encrypts anything, and should not be used with sensitive data or in a production
> environment.

## Standalone tools and services

Simulator has some components which can also be used independently:

- A `FlowChecker` which checks your flow for a default constructor and required Corda annotations.
- A `JsonMarshallingService` which can be used to convert objects to JSON and vice-versa, available through the  
  `JsonMarshallingServiceFactory`
- A `SerializationService` available through a `SerializationServiceFactory`.

# cordapp-test-utils

A selection of tools and frameworks to help with development and testing of Corda 5 CordDapps.

Note that these tools do not verify identity, check permissions, keep anything secure or encrypted, suspend / restart
flows or handle more than one version of any Flow.  They are intended only for low-level testing (meaning, providing
quick feedback and documenting examples of how things work) or demoing CorDapps. For full testing, use a real or
production-like implementation of Corda.

## CordaSim

The main class for testing your CorDapps is the CordaSim. "Uploading" your class for a given party will create a
simulated "virtual node" which can then be invoked using the same party name (in the real Corda this would be done using
the `CPI_HASH`).

```kotlin
    val corda = CordaSim()
    val member = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")
    val node = corda.createVirtualNode(member, HelloFlow::class.java)

    val response = node.callFlow(
        RPCRequestDataWrapper("r1", HelloFlow::class.java.name, "{ \"name\" : \"CordaDev\" }")
    )
```

The CordaSim will wire up your flow with lightweight versions of the same injected services that you'd get with
the real Corda.

## RPCRequestDataWrapper

Corda normally takes requests via its API in the form of JSON-formatted strings, which are converted
by Corda into an `RPCRequestData` interface. This is represented in the CordaSim by an `RPCRequestDataWrapper` class,
which allows the `RPCRequestData` to be easily constructed. There are three different construction
methods available:

- A JSON-formatted string, as you would submit with `curl`:

```kotlin
  val input = """
  {
    "httpStartFlow": {
      "clientRequestId": "r1",
      "flowClassName": "${CalculatorFlow::class.java.name}",
      "requestData":  "{ \"a\" : 6, \"b\" : 7 }"
    }
  }
  """.trimIndent()
  val requestBody = RPCRequestDataWrapper.fromJSonString(input)
```

- A three-part constructor with the request and flow classname separately, as you would submit through
  Swagger UI:

```kotlin
val requestBody = RPCRequestDataWrapper("r1", 
    "${CalculatorFlow::class.java.name}",
    "{ \"a\" : 6, \"b\" : 7 }")
```

- A three-part constructor that is strongly typed:

```kotlin
val requestBody = RPCRequestDataWrapper.fromData("r1", 
            CalculatorFlow::class.java, 
            InputMessage(6, 7))
```

## Instance vs Class upload

The CordaSim has two methods of creating nodes with responder flows:
- via a class, which will be constructed when a response flow is initialized.
- via an instance, which must be uploaded against a protocol.

Uploading an instance allows flows to be constructed containing other mocks, injected logic, etc. It also allows
the ResponderMock to be used.

## ResponderMock

The ResponderMock allows preset responses to be returned for a given request, enabling initiating flows to be tested
independently in conjunction with the CordaSim's instance-upload capability.

```kotlin
responder.whenever(CountRequest(7), listOf(CountResponse(1, 2, 3, 4, 5, 6, 7)))
cordaSim.createVirtualNode(member, "count-protocol", responder)
```

## Standalone tools and services

The CordaSim has several components which can also be used independently:

- A `CordaFlowChecker` which checks your flow for a default constructor and required Corda annotations.
- A `SimpleJSonMarshallingService` which can be used to convert objects to JSON and vice-versa.
- A `PassThroughFlowEngine` which will call any `SubFlows` you give to it.
- A `DbPersistenceService` using an in-memory HSQLDB.

Note these will eventually move to being `cordaProvided` from a factory.

## TODO:

- Check for @CordaSerializable on messages
- Handle errors for unmatched sends / receives
- Implement FlowMessaging send / receive methods
- Make FlowSession and FlowEngine mocks for InitiatingFlow tests
- Timeouts
- SigningService, MemberLookup
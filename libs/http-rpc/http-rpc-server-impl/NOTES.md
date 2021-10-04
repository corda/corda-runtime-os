# Relying more on Javalin

## Reasoning

- Reduce the amount of code we have to maintain by relying on Javalin for more functionality. Most of our code maps from class methods to HTTP endpoints and feeding input requests to these methods.
- Allow extra functionality in the future, without us having to write more code. For example, sending files over HTTP is handled by Javalin already.
- Allows us to focus on Corda related functionality rather than on the HTTP functionality.

## Assumptions

- All implementations of HTTP endpoints are internal (done by R3/Corda). 
  - Need to confirm whether this is true or not.
  - If it is not true, then either the proposed idea cannot work, or, we need to provide a thin layer over Javalin.
- That the RPC client is removed. This is because we can't maintain the mapping to interfaces that the client currently relies on.

## Outcome of POC

- Around 7.5k lines of code removed and 1.5k added (80% less code? But I think I did the maths wrong...).
- Very similar functionality. 
  - Dynamic endpoint registration.
  - Authentication.
  - Swagger UI.
  - Some of the generated OpenAPI information is lacking. We could address this by contributing back to Javalin.

## Notes

### Thin layer over Javalin

If we need to hide Javalin, then we can provide a thin layer to hide its `Context` and `ApiBuilder` classes. We can delegate down to them underneath. 

We would need to see how this would affect the OpenAPI generation though. We might have to use the DSL rather than the annotation method in this scenario.

### `MethodInvoker.kt`

Converts endpoints that return `DurableCursor`s into `DurableReturnResult`s (if the cursor is infinite). We could replace this with an extension function on Javalin's `Context` class or do it manually in each method as it ultimately is doing this so it can return it as JSON.

There is a lot of code here, but I think most of its _equivalent_ functionality shouldn't be needed. We _could_ add a way to restrict how endpoints are written like this code does (if a `DurableCursor` is returned then a `DurableContext` must be passed in) however I wouldn't say it has a super strong benefit. Either way, similar functionality could be added relying more on Javalin, however it require another layer to be built on top of Javalin which is part of what I'm trying to avoid.

```kotlin
internal open class DurableStreamsMethodInvoker(private val invocationMethod: InvocationMethod, cl: ClassLoader) :
    DefaultMethodInvoker(invocationMethod, cl) {
    private companion object {
        private val log = contextLogger()
    }

    override fun invoke(vararg args: Any?): DurableReturnResult<Any> {
        log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size}." }
        @Suppress("SpreadOperator")
        val pollResult = invokeDurableStreamMethod(*args)

        return DurableReturnResult(
            pollResult.positionedValues,
            pollResult.remainingElementsCountEstimate
        ).also { log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size} completed." } }
    }

    @Suppress("ThrowsCount")
    internal fun invokeDurableStreamMethod(vararg args: Any?): Cursor.PollResult<Any> {
        log.trace { """Invoke durable streams method "${invocationMethod.method.name}" with args size: ${args.size}.""" }
        require(args.isNotEmpty()) { throw IllegalArgumentException("Method returning Durable Streams was invoked without arguments.") }

        val (durableContexts, methodArgs) = args.partition { it is DurableStreamContext }
        if (durableContexts.size != 1) {
            val message =
                """Exactly one of the arguments is expected to be DurableStreamContext, actual: $durableContexts"""
            throw IllegalArgumentException(message)
        }
        val durableStreamContext = durableContexts.single() as DurableStreamContext

        val rpcAuthContext = CURRENT_RPC_CONTEXT.get() ?: throw FailedLoginException("Missing authentication context.")
        with(rpcAuthContext) {
            val rpcContextWithDurableStreamContext =
                this.copy(invocation = this.invocation.copy(durableStreamContext = durableStreamContext))
            CURRENT_RPC_CONTEXT.set(rpcContextWithDurableStreamContext)
        }

        @Suppress("SpreadOperator")
        val returnValue = super.invoke(*methodArgs.toTypedArray())

        val durableCursorTransferObject = uncheckedCast<Any, Supplier<Cursor.PollResult<Any>>>(returnValue as Any)
        return durableCursorTransferObject.get()
                .also {
                    log.trace {
                        """Invoke durable streams method "${invocationMethod.method.name}" with args size: ${args.size} completed."""
                    }
                }
    }
}
```

Converts endpoints that return `DurableCursor`s into `FiniteDurableReturnResult`s (if the cursor is finite). We could replace this with an extension function on Javalin's `Context` class or do it manually in each method as it ultimately is doing this so it can return it as JSON.

```kotlin
internal class FiniteDurableStreamsMethodInvoker(private val invocationMethod: InvocationMethod, cl: ClassLoader) :
    DurableStreamsMethodInvoker(invocationMethod, cl) {
    private companion object {
        private val log = contextLogger()
    }

    override fun invoke(vararg args: Any?): FiniteDurableReturnResult<Any> {
        log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size}." }
        @Suppress("SpreadOperator")
        val pollResult = invokeDurableStreamMethod(*args)
        return FiniteDurableReturnResult(
            pollResult.positionedValues,
            pollResult.remainingElementsCountEstimate,
            pollResult.isLastResult
        ).also { log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size} completed." } }
    }
}
```

For now these invokers have been removed.

Ended up replacing this functionality by having a infinite and non-infinite `PollResult` class that is returned from some endpoints. This is then mapped directly to JSON without further code. It did require some Jackson Mixin's to remove some unwanted fields.

### `ResourceToOpenApiSpecMapper` and all schema related classes

This class takes the discovered objects used as inputs and returned from methods (pretty sure that's what it does) and coverts them to OpenAPI (JSON) schemas. 

These properties are relevant in Javalin (`OpenApiOptions`):

```kotlin
/**
  * Creates a model converter, which converts a class to an open api schema.
  * Defaults to the jackson converter.
  */
var modelConverterFactory: ModelConverterFactory by LazyDefaultValue { JacksonModelConverterFactory(jacksonMapper) }
/**
  * The json mapper for creating the object api schema json. This is separated from
  * the default JavalinJson mappers.
  */
var toJsonMapper: ToJsonMapper by LazyDefaultValue { JacksonToJsonMapper(jacksonMapper) }
```

The discovery of schemas should happen automatically using Javalin, especially when in conjunction with the `@OpenApi` and `@OpenApiContent` annotations.

We can also register global _default_ schemas but this doesn't seem very flexible so we might want to stay away from it. However, we can probably make it a more "discoverable" process by allowing endpoint classes to register extra default schemas if they desire and register them in the block of code (also an example) shown below:

```kotlin
defaultDocumentation { doc ->
    doc.json("500", ErrorResponse::class.java)
    doc.json("503", ErrorResponse::class.java)
}
```

For now schema related classes have been removed.

### `SwaggerUIRenderer` OSGI related code

There is some code in `SwaggerUIRenderer` that is OSGI specific around loading the Swagger webjars. If this is handled by Javalin, then there could potentially be some issues if we cannot replicate this behaviour:

```kotlin
internal class SwaggerUIRenderer(private val configurationProvider: HttpRpcSettingsProvider) : Handler {

    private companion object {
        private val log = contextLogger()
    }

    override fun handle(ctx: Context) {
        val swaggerVersion = OptionalDependency.SWAGGERUI.version

        val bundle = FrameworkUtil.getBundle(SwaggerUIRenderer::class.java)
        if (bundle == null) {
            // This branch is used by non-OSGi tests.
            if (Util.getResourceUrl("META-INF/resources/webjars/swagger-ui/$swaggerVersion/swagger-ui.css") == null) {
                "Missing dependency '${OptionalDependency.SWAGGERUI.displayName}'".apply {
                    log.error(this)
                    throw InternalServerErrorResponse(this)
                }
            }
        } else {
            if (bundle.bundleContext.bundles.find { it.symbolicName == OptionalDependency.SWAGGERUI.symbolicName } == null) {
                "Missing dependency '${OptionalDependency.SWAGGERUI.displayName}'".apply {
                    log.error(this)
                    throw InternalServerErrorResponse(this)
                }
            }
        }
        // stuff
    }
}
```

Not currently sure what to do with this and it seems like something that needs to be addressed.

### Authorization on paths

The existing RPC code authorizes on method names of the `RpcOps` implementations. This cannot remain because their is no concept of method names when relying on Javalin. Instead we should authenticate paths, which makes sense in a RESTful setting.

Issue I am having is that I cannot call `before` to add the authorization code on each path, because I can't get access to the registered paths after registering them. This is concerning, and it doesn't look like Javalin logs them either.

We could potentially get around this with our own registration methods that will add the `before` call to each path as they are registered. Again, this adds another layer of our own code on top of Javalin which I am trying to avoid.

There is an [AccessManager](https://javalin.io/documentation#access-manager) that provides this functionality in a different way? Don't think it is as flexible as what we currently have.

I could use reflection to do this...

Used the following code:

```kotlin
 val handlerEntries = servlet().matcher.declaredField<EnumMap<HandlerType, ArrayList<HandlerEntry>>>("handlerEntries").value
val handlerTypes = HandlerType.values().toList() - HandlerType.BEFORE - HandlerType.AFTER
for (handlerType in handlerTypes) {
    for ((_, handlers) in handlerEntries) {
        for (handler in handlers) {
            // Literal translation of existing code but applying content length to all paths might be acceptable
            if (handlerType == HandlerType.POST) {
                before(handler.path) {
                    if (it.contentLength() > configurationsProvider.maxContentLength()) throw BadRequestResponse(
                        CONTENT_LENGTH_EXCEEEDS_LIMIT.format(
                            it.contentLength(),
                            configurationsProvider.maxContentLength()
                        )
                    )
                }
            }
            before(configurationsProvider.getApiVersion()) {
                authorize(authenticate(it), handler.path)
            }
        }
    }
}
```

We should consider requesting/assisting in making a change in Javalin to access this info. Feels like a reasonable request to me so might be able to do it.

In the end went for a simpler solution, that put `before` on all paths (using a wildcard) and then checked the HTTP verb and path and authorized on that.

```kotlin
before {
    val httpVerb = it.method()
    val path = it.path()
    // Literal translation of existing code but applying content length to all paths might be acceptable
    if (httpVerb == "POST") {
        log.info("POST => $path - Checking max content length")
        if (it.contentLength() > configurationsProvider.maxContentLength()) throw BadRequestResponse(
            CONTENT_LENGTH_EXCEEEDS_LIMIT.format(
                it.contentLength(),
                configurationsProvider.maxContentLength()
            )
        )
    }
    if ("swagger" !in path) {
        log.info("$httpVerb => $path - Authorizing request")
        authorize(authenticate(it), path, httpVerb)
    }
}
```

## Mapping of exceptions to http response exceptions

Can intercept all exceptions using the following code:

```kotlin
app.exception(Exception::class.java) { e, ctx ->
    // handle general exceptions here
    // will not trigger if more specific exception-mapper found
}
```

This can leverage the existing `HttpRpcServerINternal.mapToResponse` method (with a few changes) to map exceptions to responses.

### OpenAPI

- Cannot seem to document at the tag/resource level (can't put a description). You can't add an annotation to a `path` it seems.

- Our existing code generated response schemas automatically, whereas it is manual using Javalin. This isn't a big issue, however, especially for returned durable streams there is maybe a bit more going on. We can get around this using the `@OpenApiResponses` and `@OpenApiContent` annotations and then manually creating a class that represents the durable stream, e.g. :

    ```kotlin
    @OpenApi(
        requestBody = OpenApiRequestBody(
            content = [OpenApiContent(from = CalendarDaysOfTheYearRequest::class, type = "application/json")]
        ),
        responses = [
            OpenApiResponse(
                content = [OpenApiContent(from = CalendarDaysOfTheYearPollResultResponse::class, type = "application/json")],
                status = "200"
            )
        ]
    )
    private fun daysOfTheYear(ctx: Context) {
      // function
    }

    // Implementing [Cursor.PollResult] causes other fields to be added to the schema.
    data class CalendarDaysOfTheYearPollResultResponse(
        positionedValues: List<Cursor.PollResult.PositionedValue<CalendarDay>>,
        remainingElementsCountEstimate: Long?,
        isLastResult: Boolean
    )
    ```

- Javalin does not have a way to define a property as optional. Possibly could be contributed to Javalin by adding an annotation that you put on a response class's properties.

### Error messages

- Existing RPC code currently provides more user friendly errors. However we _should_ be able work around that without as much framework code. For example, missing parameters from a JSON request body or number conversions.
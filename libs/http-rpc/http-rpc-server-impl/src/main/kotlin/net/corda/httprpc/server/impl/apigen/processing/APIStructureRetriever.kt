package net.corda.httprpc.server.impl.apigen.processing

import net.corda.httprpc.durablestream.DurableStreamContext
import net.corda.v5.base.util.contextLogger
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.server.impl.apigen.models.Endpoint
import net.corda.httprpc.server.impl.apigen.models.EndpointMethod
import net.corda.httprpc.server.impl.apigen.models.EndpointParameter
import net.corda.httprpc.server.impl.apigen.models.InvocationMethod
import net.corda.httprpc.server.impl.apigen.models.Resource
import net.corda.httprpc.server.impl.apigen.models.ResponseBody
import net.corda.httprpc.server.impl.apigen.processing.streams.DurableReturnResult
import net.corda.httprpc.server.impl.apigen.processing.streams.FiniteDurableReturnResult
import net.corda.httprpc.tools.annotations.validation.HttpRpcInterfaceValidator
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.httprpc.durablestream.api.returnsDurableCursorBuilder
import net.corda.httprpc.durablestream.api.isFiniteDurableStreamsMethod
import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcDELETE
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcWS
import net.corda.httprpc.annotations.isRpcEndpointAnnotation
import net.corda.httprpc.tools.annotations.extensions.name
import net.corda.httprpc.tools.annotations.extensions.path
import net.corda.httprpc.tools.annotations.extensions.title
import net.corda.httprpc.tools.isStaticallyExposedGet
import net.corda.httprpc.tools.responseDescription
import java.lang.reflect.Method
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance
import kotlin.reflect.jvm.kotlinFunction

/**
 * [APIStructureRetriever] scans through the class, method and parameter annotations of the passed [PluggableRestResource] list,
 * generating a list of [Resource].
 */
@Suppress("UNCHECKED_CAST", "TooManyFunctions", "TooGenericExceptionThrown")
internal class APIStructureRetriever(private val opsImplList: List<PluggableRestResource<*>>) {
    private companion object {
        private val log = contextLogger()
    }

    val structure: List<Resource> by lazy { retrieveResources() }

    private val delegationTargetsMap by lazy { retrieveImplMap() }

    private fun retrieveImplMap(): Map<Class<out RestResource>, PluggableRestResource<*>> {
        try {
            log.trace { "Retrieve implementations by target interface map." }
            return opsImplList.mapNotNull { opsImpl ->
                retrieveTargetInterface(opsImpl)?.let { it to opsImpl }
            }.toMap().also { log.trace { "Retrieve implementations by target interface map completed." } }
        } catch (e: Exception) {
            "Error during retrieve implementations by target interface map".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }

    private fun retrieveResources(): List<Resource> {
        try {
            log.trace { "Retrieve resources." }
            return opsImplList.mapNotNull {
                retrieveTargetInterface(it)
            }.validated().map { c ->
                val annotation = c.annotations.find { annotation -> annotation is HttpRpcResource } as HttpRpcResource
                val basePath = annotation.path(c)
                Resource(
                    annotation.name(c),
                    annotation.description,
                    basePath,
                    retrieveEndpoints(c).toSet()
                )
            }.also { log.trace { "Retrieve resources completed." } }
        } catch (e: Exception) {
            "Error during retrieve resources".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }

    private fun List<Class<out RestResource>>.validated(): List<Class<out RestResource>> {
        try {
            log.trace { "Validate resource classes: ${this.joinToString(",")}." }
            return this.apply {
                val result = HttpRpcInterfaceValidator.validate(this)
                if (result.errors.isNotEmpty()) {
                    throw IllegalArgumentException(
                        "Errors when validate resource classes:\n${
                            result.errors.joinToString(
                                "\n"
                            )
                        }"
                    )
                }
            }.also { log.trace { "Validate resource classes: ${this.joinToString(",")} completed." } }
        } catch (e: Exception) {
            "Error during validate".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }

    private fun retrieveTargetInterface(impl: PluggableRestResource<*>): Class<out RestResource>? {
        try {
            log.trace { "Retrieve target interface for implementation \"${impl::class.java.name}\"." }
            return impl.targetInterface.takeIf { it.annotations.any { annotation -> annotation is HttpRpcResource } }
                    .also {
                        log.trace {
                            "Retrieved target interface \"${impl.targetInterface.name}\" " +
                                    "for implementation \"${impl::class.java.name}\" completed."
                        }
                    }
        } catch (e: Exception) {
            "Error during retrieve target interface for implementation \"${impl::class.java.name}\"".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }

    private fun retrieveEndpoints(clazz: Class<out RestResource>): List<Endpoint> {
        try {
            log.trace { "Retrieve endpoints." }
            val methods = clazz.methods
            return (getEndpointsByAnnotations(methods) + getImplicitGETEndpoints(methods, clazz))
                .also { log.trace { "Retrieve endpoints completed." } }
        } catch (e: Exception) {
            "Error during retrieve endpoints".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }

    private fun getEndpointsByAnnotations(methods: Array<Method>) =
        methods.sortedBy { it.name }.filter { method ->
            method.annotations.singleOrNull { it.isRpcEndpointAnnotation() } != null
        }.map { method ->
            method.toEndpoint()
        }

    private fun getImplicitGETEndpoints(methods: Array<Method>, clazz: Class<out RestResource>) =
        methods.filter {
            isImplicitlyExposedGETMethod(it)
        }.map { method ->
            HttpRpcGET::class.createInstance().let { annotation ->
                Endpoint(
                    EndpointMethod.GET,
                    annotation.title(method),
                    annotation.description,
                    annotation.path(method),
                    method.retrieveParameters(),
                    ResponseBody(
                        method.responseDescription,
                        method.toClassAndParameterizedTypes().first,
                        method.toClassAndParameterizedTypes().second,
                        method.kotlinFunction?.returnType?.isMarkedNullable ?: false
                    ),
                    method.getInvocationMethod(clazz)
                )
            }
        }

    private fun isImplicitlyExposedGETMethod(method: Method): Boolean {
        log.debug { "Is implicitly exposed method check for method \"${method.name} \"." }
        return (method.isStaticallyExposedGet())
            .also {
                log.trace { "Is implicitly exposed method check for method \"${method.name} \", " +
                        "result \"$it\" completed." }
            }
    }

    @Suppress("ComplexMethod")
    private fun Method.toEndpoint(): Endpoint {
        try {
            log.trace { "Method \"${this.name}\" to endpoint." }
            val annotation = this.annotations.single { it.isRpcEndpointAnnotation() }
            return when (annotation) {
                is HttpRpcGET -> this.toGETEndpoint(annotation)
                is HttpRpcPOST -> this.toPOSTEndpoint(annotation)
                is HttpRpcPUT -> this.toPUTEndpoint(annotation)
                is HttpRpcDELETE -> this.toDELETEEndpoint(annotation)
                is HttpRpcWS -> this.toWSEndpoint(annotation)
                else -> throw IllegalArgumentException("Unknown endpoint type for: ${this.name}")
            }.also { log.trace { "Method \"${this.name}\" to endpoint completed." } }
        } catch (e: Exception) {
            "Error during Method \"${this.name}\" to endpoint".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }

    private fun Method.toGETEndpoint(annotation: HttpRpcGET): Endpoint {
        log.trace { """Method "$name" to GET endpoint.""" }
        return Endpoint(
            EndpointMethod.GET,
            annotation.title(this),
            annotation.description,
            annotation.path(this),
            retrieveParameters(),
            ResponseBody(
                annotation.responseDescription,
                this.toClassAndParameterizedTypes().first,
                this.toClassAndParameterizedTypes().second,
                this.kotlinFunction?.returnType?.isMarkedNullable ?: false
            ),
            this.getInvocationMethod()
        ).also { log.trace { """"Method "$name" to GET endpoint completed.""" } }
    }

    private fun Method.toDELETEEndpoint(annotation: HttpRpcDELETE): Endpoint {
        log.trace { """Method "$name" to DELETE endpoint.""" }
        return Endpoint(
            EndpointMethod.DELETE,
            annotation.title(this),
            annotation.description,
            annotation.path(),
            retrieveParameters(),
            ResponseBody(
                annotation.responseDescription,
                this.toClassAndParameterizedTypes().first,
                this.toClassAndParameterizedTypes().second,
                this.kotlinFunction?.returnType?.isMarkedNullable ?: false
            ),
            this.getInvocationMethod()
        ).also { log.trace { """"Method "$name" to DELETE endpoint completed.""" } }
    }

    private fun Method.toPOSTEndpoint(annotation: HttpRpcPOST): Endpoint {
        log.trace { "Method \"${this.name}\" to POST endpoint." }
        val isReturnTypeNullable = this.kotlinFunction?.returnType?.isMarkedNullable ?: false
        val responseBody = when {
            this.returnsDurableCursorBuilder() && !this.isFiniteDurableStreamsMethod() -> {
                ResponseBody(
                    annotation.responseDescription,
                    DurableReturnResult::class.java,
                    this.toClassAndParameterizedTypes().second,
                    isReturnTypeNullable
                )
            }
            this.isFiniteDurableStreamsMethod() -> {
                ResponseBody(
                    annotation.responseDescription,
                    FiniteDurableReturnResult::class.java,
                    this.toClassAndParameterizedTypes().second,
                    isReturnTypeNullable
                )
            }
            else -> {
                ResponseBody(
                    annotation.responseDescription,
                    this.toClassAndParameterizedTypes().first,
                    this.toClassAndParameterizedTypes().second,
                    isReturnTypeNullable
                )
            }
        }

        return Endpoint(
            EndpointMethod.POST,
            annotation.title(this),
            annotation.description,
            annotation.path(),
            retrieveParameters(true),
            responseBody,
            this.getInvocationMethod()
        ).also { log.trace { "Method \"${this.name}\" to POST endpoint completed." } }
    }

    private fun Method.toPUTEndpoint(annotation: HttpRpcPUT): Endpoint {
        log.trace { "Method \"${this.name}\" to PUT endpoint." }
        val isReturnTypeNullable = this.kotlinFunction?.returnType?.isMarkedNullable ?: false
        val responseBody = when {
            this.returnsDurableCursorBuilder() && !this.isFiniteDurableStreamsMethod() -> {
                ResponseBody(
                    annotation.responseDescription,
                    DurableReturnResult::class.java,
                    this.toClassAndParameterizedTypes().second,
                    isReturnTypeNullable
                )
            }
            this.isFiniteDurableStreamsMethod() -> {
                ResponseBody(
                    annotation.responseDescription,
                    FiniteDurableReturnResult::class.java,
                    this.toClassAndParameterizedTypes().second,
                    isReturnTypeNullable
                )
            }
            else -> {
                ResponseBody(
                    annotation.responseDescription,
                    this.toClassAndParameterizedTypes().first,
                    this.toClassAndParameterizedTypes().second,
                    isReturnTypeNullable
                )
            }
        }

        return Endpoint(
            EndpointMethod.PUT,
            annotation.title(this),
            annotation.description,
            annotation.path(),
            retrieveParameters(true),
            responseBody,
            this.getInvocationMethod()
        ).also { log.trace { "Method \"${this.name}\" to PUT endpoint completed." } }
    }

    private fun Method.toWSEndpoint(annotation: HttpRpcWS): Endpoint {
        log.trace { """Method "$name" to WS endpoint.""" }
        return Endpoint(
            EndpointMethod.WS,
            annotation.title(this),
            annotation.description,
            annotation.path(),
            retrieveParameters(),
            ResponseBody(
                annotation.responseDescription,
                this.toClassAndParameterizedTypes().first,
                this.toClassAndParameterizedTypes().second,
                this.kotlinFunction?.returnType?.isMarkedNullable ?: false
            ),
            this.getInvocationMethod()
        ).also { log.trace { """"Method "$name" to WS endpoint completed.""" } }
    }

    private fun Method.getInvocationMethod(clazz: Class<out RestResource>? = null): InvocationMethod {
        try {
            log.debug { "Get invocation method for \"${this.name}\"." }
            return InvocationMethod(
                this,
                // declaring class may be the RPCOps in case of implicitly exposed functions,
                // so direct class scanning now must also be checked
                delegationTargetsMap[this.declaringClass]
                    ?: delegationTargetsMap[clazz]
                    ?: throw NoSuchElementException("No valid implementation for  \"${this.declaringClass.name}#${this.name} \" found.")
            ).also { log.trace { "Get invocation method for \"${this.name}\" completed." } }
        } catch (e: Exception) {
            "Error during Get invocation method for \"${this.name}\"".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }

    private fun Method.kotlinValueKParameters(): List<KParameter> {
        return this.kotlinFunction?.parameters?.filter { it.kind == KParameter.Kind.VALUE } ?: emptyList()
    }

    private fun Method.retrieveParameters(includeContextParam: Boolean = false): List<EndpointParameter> {
        try {
            log.trace { """Retrieve parameters for method "$name".""" }
            val methodParams = this.kotlinValueKParameters().map { ParametersTransformerFactory.create(it).transform() }
            val contextParam = ParametersTransformerFactory.create(
                "context",
                DurableStreamContext::class.java.toEndpointParameterParameterizedType()!!
            )
                .transform()

            return when {
                returnsDurableCursorBuilder() && includeContextParam -> methodParams.plus(contextParam)
                else -> methodParams
            }.also { log.trace { """Retrieve parameters for method "$name" completed.""" } }
        } catch (e: Exception) {
            """Error during Retrieve parameters for method "$name".""".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }
}

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
import net.corda.v5.base.util.Try
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.v5.base.stream.returnsDurableCursorBuilder
import net.corda.v5.base.stream.isFiniteDurableStreamsMethod
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import net.corda.httprpc.tools.annotations.extensions.name
import net.corda.httprpc.tools.annotations.extensions.path
import net.corda.httprpc.tools.annotations.extensions.title
import net.corda.httprpc.tools.staticExposedGetMethods
import java.lang.reflect.Method
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance
import kotlin.reflect.jvm.kotlinFunction

/**
 * [APIStructureRetriever] scans through the class, method and parameter annotations of the passed [PluggableRPCOps] list,
 * generating a list of [Resource].
 */
@Suppress("UNCHECKED_CAST", "TooManyFunctions", "TooGenericExceptionThrown", "TooGenericExceptionCaught")
class APIStructureRetriever(private val opsImplList: List<PluggableRPCOps<*>>) {
    private companion object {
        private val log = contextLogger()
    }

    val structure: Try<List<Resource>> by lazy { Try.on { retrieveResources() } }
    private val delegationTargetsMap by lazy { retrieveImplMap() }

    private fun retrieveImplMap(): Map<Class<out RpcOps>, PluggableRPCOps<*>> {
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

    private fun List<Class<out RpcOps>>.validated(): List<Class<out RpcOps>> {
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

    private fun retrieveTargetInterface(impl: PluggableRPCOps<*>): Class<out RpcOps>? {
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

    private fun retrieveEndpoints(clazz: Class<out RpcOps>): List<Endpoint> {
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
        methods.filter {
            hasEndpointAnnotation(it)
        }.map { method ->
            method.toEndpoint()
        }

    private fun getImplicitGETEndpoints(methods: Array<Method>, clazz: Class<out RpcOps>) =
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
                        annotation.responseDescription,
                        method.toClassAndParameterizedTypes().first,
                        method.toClassAndParameterizedTypes().second
                    ),
                    method.getInvocationMethod(clazz)
                )
            }
        }

    private fun isImplicitlyExposedGETMethod(method: Method): Boolean {
        log.debug { "Is implicitly exposed method check for method \"${method.name} \"." }
        val matchingMethod = staticExposedGetMethods.firstOrNull { it.equals(method.name, true) }
        return (matchingMethod != null)
            .also {
                log.trace { "Is implicitly exposed method check for method \"${method.name} \", " +
                        "result \"$it\", matching method name \"$matchingMethod\" completed." }
            }
    }

    @SuppressWarnings("ComplexMethod")
    private fun hasEndpointAnnotation(method: Method): Boolean {
        try {
            log.trace { "Has endpoint annotation check for method \"${method.name}\"." }
            val countGET = method.annotations.count { annotation -> annotation is HttpRpcGET }
            val countPOST = method.annotations.count { annotation -> annotation is HttpRpcPOST }
            val count = countGET.plus(countPOST)
            return when (count) {
                1 -> true
                0 -> false
                else -> throw IllegalArgumentException("Only one of ${HttpRpcPOST::class.simpleName}, " +
                        "${HttpRpcGET::class.simpleName} can be specified on an endpoint")
            }.also {
                val annotationTypeText = if (countGET > 0) "HttpRpcGET" else "HttpRpcPOST"
                log.trace {
                    "Has endpoint annotation check for method \"${method.name}\" " +
                            "with annotation $annotationTypeText found completed."
                }
            }
        } catch (e: Exception) {
            "Error during endpoint annotation check for method \"${method.name}\" ".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }

    private fun Method.toEndpoint(): Endpoint {
        try {
            log.trace { "Method \"${this.name}\" to endpoint." }
            val annotation = this.annotations.single { it is HttpRpcPOST || it is HttpRpcGET }
            return when (annotation) {
                is HttpRpcGET -> this.toGETEndpoint(annotation)
                is HttpRpcPOST -> this.toPOSTEndpoint(annotation)
                else -> throw IllegalArgumentException("Unknown endpoint type")
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
                this.toClassAndParameterizedTypes().second
            ),
            this.getInvocationMethod()
        ).also { log.trace { """"Method "$name" to GET endpoint completed.""" } }
    }

    private fun Method.toPOSTEndpoint(annotation: HttpRpcPOST): Endpoint {
        log.trace { "Method \"${this.name}\" to POST endpoint." }
        val responseBody = when {
            this.returnsDurableCursorBuilder() && !this.isFiniteDurableStreamsMethod() -> {
                ResponseBody(
                    annotation.responseDescription,
                    DurableReturnResult::class.java,
                    this.toClassAndParameterizedTypes().second
                )
            }
            this.isFiniteDurableStreamsMethod() -> {
                ResponseBody(
                    annotation.responseDescription,
                    FiniteDurableReturnResult::class.java,
                    this.toClassAndParameterizedTypes().second
                )
            }
            else -> {
                ResponseBody(
                    annotation.responseDescription,
                    this.toClassAndParameterizedTypes().first,
                    this.toClassAndParameterizedTypes().second
                )
            }
        }

        return Endpoint(
            EndpointMethod.POST,
            annotation.title(this),
            annotation.description,
            annotation.path(this),
            retrieveParameters(true),
            responseBody,
            this.getInvocationMethod()
        ).also { log.trace { "Method \"${this.name}\" to POST endpoint completed." } }
    }

    private fun Method.getInvocationMethod(clazz: Class<out RpcOps>? = null): InvocationMethod {
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

package net.corda.rest.server.impl.apigen.processing

import io.javalin.websocket.WsConfig
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.durablestream.api.isFiniteDurableStreamsMethod
import net.corda.rest.durablestream.api.returnsDurableCursorBuilder
import net.corda.rest.server.impl.apigen.models.Endpoint
import net.corda.rest.server.impl.apigen.models.EndpointMethod
import net.corda.rest.server.impl.apigen.models.EndpointParameter
import net.corda.rest.server.impl.apigen.models.Resource
import net.corda.rest.server.impl.internal.HttpExceptionMapper
import net.corda.rest.server.impl.security.RestAuthenticationProvider
import net.corda.rest.server.impl.security.provider.credentials.DefaultCredentialResolver
import net.corda.rest.server.impl.websocket.WebSocketCloserService
import net.corda.rest.server.impl.websocket.WebSocketRouteAdaptor
import net.corda.rest.tools.HttpPathUtils.joinResourceAndEndpointPaths
import net.corda.rest.tools.isDuplexChannel
import net.corda.rest.tools.isStaticallyExposedGet
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException

/**
 * [RouteProvider] implementations are responsible for returning route mapping information to the requesting server implementation.
 *
 */
internal interface RouteProvider {
    val httpNoAuthRequiredGetRoutes: List<RouteInfo>
    val httpGetRoutes: List<RouteInfo>
    val httpPostRoutes: List<RouteInfo>
    val httpPutRoutes: List<RouteInfo>
    val httpDeleteRoutes: List<RouteInfo>
    val httpDuplexRoutes: List<RouteInfo>
}

internal class JavalinRouteProviderImpl(
    private val basePath: String,
    private val resources: List<Resource>
) : RouteProvider {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val httpNoAuthRequiredGetRoutes = mapResourcesToRoutesByHttpMethod(EndpointMethod.GET)
        .filter { routeInfo ->
            routeInfo.method.method.isStaticallyExposedGet()
        }

    override val httpGetRoutes = mapResourcesToRoutesByHttpMethod(EndpointMethod.GET)
        .filterNot { routeInfo ->
            routeInfo.method.method.isStaticallyExposedGet()
        }

    override val httpPostRoutes = mapResourcesToRoutesByHttpMethod(EndpointMethod.POST)

    override val httpPutRoutes = mapResourcesToRoutesByHttpMethod(EndpointMethod.PUT)

    override val httpDeleteRoutes = mapResourcesToRoutesByHttpMethod(EndpointMethod.DELETE)

    override val httpDuplexRoutes = mapResourcesToRoutesByHttpMethod(EndpointMethod.WS)

    private fun mapResourcesToRoutesByHttpMethod(httpMethod: EndpointMethod): List<RouteInfo> {
        log.trace { "Map resources to routes by http method." }
        return resources.flatMap { resource ->
            resource.endpoints.filter { it.method == httpMethod }
                .flatMap { endpoint ->
                    combineResourceAndEndpointApiVersions(
                        resource.apiVersions,
                        endpoint.apiVersions
                    ).map { apiVersion ->
                        RouteInfo(basePath, resource.path, apiVersion, endpoint)
                    }
                }
        }.also { log.trace { "Map resources to routes by http method completed." } }
    }

    private fun combineResourceAndEndpointApiVersions(
        resourceVersions: Set<RestApiVersion>,
        endpointVersions: Set<RestApiVersion>
    ): Set<RestApiVersion> {
        // Returns a simple intersection, however in the future additional criteria might be necessary such as
        // global cut-off version
        return resourceVersions.intersect(endpointVersions)
    }
}

internal enum class ParameterType {
    PATH, QUERY, BODY
}

internal data class Parameter(
    val classType: Class<*>,
    val name: String,
    val type: ParameterType,
    val required: Boolean,
    val nullable: Boolean,
    val default: String?,
    val isFileUpload: Boolean
)

internal class RouteInfo(
    private val basePath: String,
    private val resourcePath: String,
    private val apiVersion: RestApiVersion,
    private val endpoint: Endpoint
) {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    val parameters = mapEndpointParameters(endpoint.parameters)
    val fullPath get() = generateFullPath(resourcePath, endpoint.path)
    val method get() = endpoint.invocationMethod
    val isMultipartFileUpload get() = endpoint.parameters.any { it.isFile }

    private val methodInvoker = when {
        endpoint.invocationMethod.method.isFiniteDurableStreamsMethod() ->
            FiniteDurableStreamsMethodInvoker(endpoint.invocationMethod)
        endpoint.invocationMethod.method.returnsDurableCursorBuilder() &&
            !endpoint.invocationMethod.method.isFiniteDurableStreamsMethod() ->
            DurableStreamsMethodInvoker(endpoint.invocationMethod)
        else -> DefaultMethodInvoker(endpoint.invocationMethod)
    }

    @Suppress("SpreadOperator")
    fun invokeDelegatedMethod(vararg args: Any?): Any? {
        log.trace { "Invoke delegated method \"${endpoint.invocationMethod.method.name}\" with args size: ${args.size}." }
        try {
            return methodInvoker.invoke(*args)
                .also {
                    log.trace {
                        "Invoke delegated method \"${endpoint.invocationMethod.method.name}\" with args size: ${args.size} completed."
                    }
                }
        } catch (e: InvocationTargetException) {
            e.cause?.let { throw it } ?: throw e
        }
    }

    private fun generateFullPath(resourcePath: String, endpointPath: String?): String {
        val combinedPath =
            joinResourceAndEndpointPaths("/$basePath/${apiVersion.versionPath}/$resourcePath", endpointPath)
        return combinedPath.lowercase().also {
            log.trace { "Full path $it generated." }
        }
    }

    private fun mapEndpointParameters(parameters: List<EndpointParameter>): List<Parameter> {
        log.trace { "Map endpoint parameters of endpoint \"$fullPath\" to route provider parameters, list size: \"${parameters.size}\"." }
        return parameters.filterNot { it.classType.isDuplexChannel() }.map {
            log.trace { "Map endpoint parameter \"${it.name}\"." }
            Parameter(
                it.classType,
                it.name,
                ParameterType.valueOf(it.type.name),
                it.required,
                it.nullable,
                it.default,
                it.isFile
            ).also { result ->
                log.trace { "Map endpoint parameter \"${it.name}\" completed. Result: \"$result\"" }
            }
        }.also {
            log.trace { "Map endpoint parameters of endpoint \"$fullPath\" to route provider parameters completed." }
        }
    }

    internal fun setupWsCall(
        restAuthProvider: RestAuthenticationProvider,
        credentialResolver: DefaultCredentialResolver,
        webSocketCloserService: WebSocketCloserService,
        adaptors: MutableList<AutoCloseable>,
        webSocketIdleTimeoutMs: Long
    ): (WsConfig) -> Unit {
        return { wsConfig ->
            try {
                val adaptor = WebSocketRouteAdaptor(
                    this,
                    restAuthProvider,
                    credentialResolver,
                    webSocketCloserService,
                    webSocketIdleTimeoutMs
                )
                wsConfig.onMessage(adaptor)
                wsConfig.onClose(adaptor)
                wsConfig.onConnect(adaptor)
                wsConfig.onError(adaptor)

                adaptors.add(adaptor)
                log.info("Setup for WS call for \"$fullPath\" completed.")
            } catch (e: Exception) {
                log.warn("Error setting-up WS call for \"$fullPath\"", e)
                throw HttpExceptionMapper.mapToResponse(e)
            }
        }
    }
}

package net.corda.httprpc.server.impl.apigen.processing

import io.javalin.websocket.WsConfig
import net.corda.httprpc.server.impl.apigen.models.Endpoint
import net.corda.httprpc.server.impl.apigen.models.EndpointMethod
import net.corda.httprpc.server.impl.apigen.models.EndpointParameter
import net.corda.httprpc.server.impl.apigen.models.Resource
import net.corda.httprpc.server.impl.websocket.WebSocketRouteAdaptor
import net.corda.httprpc.server.impl.internal.HttpExceptionMapper
import net.corda.httprpc.server.impl.security.RestAuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.credentials.DefaultCredentialResolver
import net.corda.httprpc.tools.HttpPathUtils.joinResourceAndEndpointPaths
import net.corda.httprpc.tools.isDuplexChannel
import net.corda.httprpc.tools.isStaticallyExposedGet
import net.corda.httprpc.durablestream.api.isFiniteDurableStreamsMethod
import net.corda.httprpc.durablestream.api.returnsDurableCursorBuilder
import org.slf4j.LoggerFactory
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import java.lang.reflect.InvocationTargetException
import net.corda.httprpc.server.impl.websocket.WebSocketCloserService

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
    private val apiVersion: String,
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
                .map { RouteInfo(basePath, resource.path, apiVersion, it) }

        }.also { log.trace { "Map resources to routes by http method completed." } }
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
    private val apiVersion: String,
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
        endpoint.invocationMethod.method.returnsDurableCursorBuilder()
                && !endpoint.invocationMethod.method.isFiniteDurableStreamsMethod() ->
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
        val combinedPath = joinResourceAndEndpointPaths("/${basePath}/v${apiVersion}/${resourcePath}", endpointPath)
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
            log.info("Setting-up WS call for '$fullPath'")
            try {
                val adaptor = WebSocketRouteAdaptor(
                    this, restAuthProvider, credentialResolver, webSocketCloserService, webSocketIdleTimeoutMs
                )
                wsConfig.onMessage(adaptor)
                wsConfig.onClose(adaptor)
                wsConfig.onConnect(adaptor)
                wsConfig.onError(adaptor)

                adaptors.add(adaptor)
                log.debug { "Setting-up WS call for '$fullPath' completed." }
            } catch (e: Exception) {
                log.warn("Error Setting-up WS call for '$fullPath'", e)
                throw HttpExceptionMapper.mapToResponse(e)
            }
        }
    }
}

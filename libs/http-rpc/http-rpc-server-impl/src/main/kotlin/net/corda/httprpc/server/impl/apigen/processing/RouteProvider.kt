package net.corda.httprpc.server.impl.apigen.processing

import net.corda.v5.base.util.contextLogger
import net.corda.httprpc.server.impl.apigen.models.Endpoint
import net.corda.httprpc.server.impl.apigen.models.EndpointMethod
import net.corda.httprpc.server.impl.apigen.models.EndpointParameter
import net.corda.httprpc.server.impl.apigen.models.Resource
import net.corda.httprpc.tools.HttpPathUtils.joinResourceAndEndpointPaths
import net.corda.v5.base.util.trace
import net.corda.v5.base.stream.isFiniteDurableStreamsMethod
import net.corda.v5.base.stream.returnsDurableCursorBuilder
import net.corda.httprpc.tools.annotations.validation.utils.pathParamRegex
import net.corda.httprpc.tools.isStaticallyExposedGet
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
}

internal class JavalinRouteProviderImpl(
    private val basePath: String,
    private val apiVersion: String,
    private val resources: List<Resource>
) : RouteProvider {

    private companion object {
        private val log = contextLogger()
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

    private fun mapResourcesToRoutesByHttpMethod(httpMethod: EndpointMethod): List<RouteInfo> {
        log.trace { "Map resources to routes by http method." }
        return resources.flatMap { resource ->
            resource.endpoints.filter { it.method == httpMethod }
                .map { it.copy(path = replacePathParametersInEndpointPath(it.path)) }
                .map { RouteInfo(basePath, resource.path, apiVersion, it) }

        }.also { log.trace { "Map resources to routes by http method completed." } }
    }

    private fun replacePathParametersInEndpointPath(path: String?): String? =
        path?.replace(pathParamRegex) { matchResult -> ":${matchResult.groupValues[1]}" }
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
    val default: String?
)

internal class RouteInfo(
    private val basePath: String,
    private val resourcePath: String,
    private val apiVersion: String,
    private val endpoint: Endpoint
) {
    private companion object {
        private val log = contextLogger()
    }

    val parameters = mapEndpointParameters(endpoint.parameters)
    val fullPath get() = generateFullPath(resourcePath, endpoint.path)
    val method get() = endpoint.invocationMethod
    private val methodInvoker = when {
        endpoint.invocationMethod.method.isFiniteDurableStreamsMethod() ->
            FiniteDurableStreamsMethodInvoker(endpoint.invocationMethod)
        endpoint.invocationMethod.method.returnsDurableCursorBuilder()
                && !endpoint.invocationMethod.method.isFiniteDurableStreamsMethod() ->
            DurableStreamsMethodInvoker(endpoint.invocationMethod
        )
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
        return parameters.map {
            log.trace { "Map endpoint parameter \"${it.name}\"." }
            Parameter(
                it.classType,
                it.name,
                ParameterType.valueOf(it.type.name),
                it.required,
                it.nullable,
                it.default
            ).also { result ->
                log.trace { "Map endpoint parameter \"${it.name}\" completed. Result: \"$result\"" }
            }
        }.also {
            log.trace { "Map endpoint parameters of endpoint \"$fullPath\" to route provider parameters completed." }
        }
    }
}

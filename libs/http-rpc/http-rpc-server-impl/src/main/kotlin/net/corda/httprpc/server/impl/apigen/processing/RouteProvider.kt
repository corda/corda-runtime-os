package net.corda.httprpc.server.impl.apigen.processing

import net.corda.v5.base.util.contextLogger
import net.corda.httprpc.rpc.proxies.RpcAuthHelper
import net.corda.httprpc.server.impl.apigen.models.Endpoint
import net.corda.httprpc.server.impl.apigen.models.EndpointMethod
import net.corda.httprpc.server.impl.apigen.models.EndpointParameter
import net.corda.httprpc.server.impl.apigen.models.Resource
import net.corda.v5.base.util.trace
import net.corda.v5.base.stream.isFiniteDurableStreamsMethod
import net.corda.v5.base.stream.returnsDurableCursorBuilder
import net.corda.httprpc.tools.annotations.validation.utils.pathParamRegex
import net.corda.httprpc.tools.staticExposedGetMethods
import java.lang.reflect.InvocationTargetException

/**
 * [RouteProvider] implementations are responsible for returning route mapping information to the requesting server implementation.
 *
 */
internal interface RouteProvider {
    val httpNoAuthRequiredGetRoutes: List<RouteInfo>
    val httpGetRoutes: List<RouteInfo>
    val httpPostRoutes: List<RouteInfo>
}

internal class JavalinRouteProviderImpl(
    private val basePath: String,
    private val apiVersion: String,
    private val resources: List<Resource>
) : RouteProvider {

    private companion object {
        private val log = contextLogger()

        private val noAuthRequiredGETEndpoints = staticExposedGetMethods
    }

    override val httpNoAuthRequiredGetRoutes = mapResourcesToRoutesByHttpMethod(EndpointMethod.GET)
        .filter { routeInfo ->
            val methodName = routeInfo.method.method.name
            noAuthRequiredGETEndpoints.any { methodName.equals(it, true) }
        }
    override val httpGetRoutes = mapResourcesToRoutesByHttpMethod(EndpointMethod.GET)
        .filter { routeInfo ->
            val methodName = routeInfo.method.method.name
            noAuthRequiredGETEndpoints.none { methodName.equals(it, true) }
        }
    override val httpPostRoutes = mapResourcesToRoutesByHttpMethod(EndpointMethod.POST)

    private fun mapResourcesToRoutesByHttpMethod(httpMethod: EndpointMethod): List<RouteInfo> {
        log.trace { "Map resources to routes by http method." }
        return resources.flatMap { resource ->
            resource.endpoints.filter { it.method == httpMethod }
                .map { it.copy(path = replacePathParametersInEndpointPath(it.path)) }
                .map { RouteInfo(basePath, resource.path, apiVersion, it) }

        }.also { log.trace { "Map resources to routes by http method completed." } }
    }

    private fun replacePathParametersInEndpointPath(path: String): String =
        path.replace(pathParamRegex) { matchResult -> ":${matchResult.groupValues[1]}" }
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
    val methodFullName get() = RpcAuthHelper.methodFullName(endpoint.invocationMethod.method)
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
        println("QQQ invokeDelegatedMethod 1")
        println("QQQ invokeDelegatedMethod 2 - ${endpoint.invocationMethod.method.name}")
        println("QQQ invokeDelegatedMethod 3 - ${methodInvoker}")
        log.trace { "Invoke delegated method \"${endpoint.invocationMethod.method.name}\" with args size: ${args.size}." }
        try {
            return methodInvoker.invoke(*args)
                    .also {
                        log.trace {
                            "Invoke delegated method \"${endpoint.invocationMethod.method.name}\" with args size: ${args.size} completed."
                        }
                    }
        } catch (e: InvocationTargetException) {
            Exception("QQQ invokeDelegatedMethod got error", e).printStackTrace(System.out)
            e.printStackTrace()
            e.cause?.let { throw it } ?: throw e
        }
        catch (e: Exception) {
            Exception("QQQ invokeDelegatedMethod got another error", e).printStackTrace(System.out)
            throw e
        }
    }

    private fun generateFullPath(resourcePath: String, endpointPath: String): String {
        log.trace { "Generate full path for resource path: \"$resourcePath\", endpoint path: \"$endpointPath\"." }
        return "/${basePath}/v${apiVersion}/${resourcePath}/${endpointPath}".toLowerCase().also {
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

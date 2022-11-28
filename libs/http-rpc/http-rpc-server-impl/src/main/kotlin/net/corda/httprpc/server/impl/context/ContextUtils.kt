package net.corda.httprpc.server.impl.context

import io.javalin.core.util.Header
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.UnauthorizedResponse
import net.corda.httprpc.security.Actor
import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.security.InvocationContext
import net.corda.httprpc.security.RpcAuthContext
import net.corda.httprpc.security.rpcContext
import net.corda.httprpc.server.impl.apigen.processing.RouteInfo
import net.corda.httprpc.server.impl.context.ClientRequestContext.Companion.METHOD_SEPARATOR
import net.corda.httprpc.server.impl.internal.HttpExceptionMapper
import net.corda.httprpc.server.impl.internal.ParameterRetrieverFactory
import net.corda.httprpc.server.impl.internal.ParametersRetrieverContext
import net.corda.httprpc.server.impl.security.HttpRpcSecurityManager
import net.corda.httprpc.server.impl.security.provider.credentials.CredentialResolver
import net.corda.metrics.CordaMetrics
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.lang.IllegalArgumentException
import javax.security.auth.login.FailedLoginException

internal object ContextUtils {

    private val log = LoggerFactory.getLogger(ContextUtils::class.java)

    const val contentTypeApplicationJson = "application/json"

    private const val CORDA_X500_NAME = "O=Http RPC Server, L=New York, C=US"

    private const val USER_MDC = "http.user"
    private const val METHOD_MDC = "http.method"
    private const val PATH_MDC = "http.path"

    private fun <T> withMDC(user: String, method: String, path: String, block: () -> T): T {
        return listOf(USER_MDC to user, METHOD_MDC to method, PATH_MDC to path).toMap().withMDC(block)
    }

    private fun <T> Map<String, String>.withMDC(block: () -> T): T {
        forEach { MDC.put(it.key, it.value) }
        return try {
            block()
        } finally {
            keys.forEach { MDC.remove(it) }
        }
    }

    private fun String.loggerFor(): Logger {
        return LoggerFactory.getLogger(ContextUtils::class.java.name + "." + this)
    }

    fun authenticate(
        ctx: ClientRequestContext,
        securityManager: HttpRpcSecurityManager,
        credentialResolver: CredentialResolver
    ): AuthorizingSubject {
        log.trace { "Authenticate request." }
        log.debug { """Authenticate for path: "${ctx.path}".""" }

        val credentials = credentialResolver.resolve(ctx)
            ?: """User credentials are empty or cannot be resolved""".let {
                log.info(it)
                ctx.addWwwAuthenticateHeaders(securityManager)
                throw UnauthorizedResponse(it)
            }

        try {
            return securityManager.authenticate(credentials).also {
                val rpcAuthContext = RpcAuthContext(
                    InvocationContext(
                        Actor.service(
                            this::javaClass.toString(),
                            MemberX500Name.parse(CORDA_X500_NAME)
                        )
                    ), it
                )
                CURRENT_RPC_CONTEXT.set(rpcAuthContext)
                log.trace { """Authenticate user "${it.principal}" completed.""" }
            }
        } catch (e: FailedLoginException) {
            "Error during user authentication".let {
                log.warn("$it: ${e.message}")
                ctx.addWwwAuthenticateHeaders(securityManager)
                throw UnauthorizedResponse(it)
            }
        }
    }

    private fun validateRequestContentType(routeInfo: RouteInfo, ctx: Context) {
        val expectsMultipart = routeInfo.isMultipartFileUpload
        val receivesMultipartRequest = ctx.isMultipart()

        if(expectsMultipart && !receivesMultipartRequest) {
            throw IllegalArgumentException("Endpoint expects Content-Type [multipart/form-data] but received [${ctx.contentType()}].")
        } else if(receivesMultipartRequest && !expectsMultipart) {
            throw IllegalArgumentException("Unexpected Content-Type [${ctx.contentType()}].")
        }
    }

    fun RouteInfo.invokeHttpMethod(): (Context) -> Unit {
        return { ctx ->
            val ctxMethod = ctx.method()
            withMDC(rpcContext()?.principal ?: "<anonymous>", ctxMethod, ctx.path()) {
                val methodLogger = ctxMethod.loggerFor()
                methodLogger.info("Servicing $ctxMethod request to '${ctx.path()}'")
                methodLogger.debug { "Invoke method \"${method.method.name}\" for route info." }
                methodLogger.trace { "Get parameter values." }

                CordaMetrics.Metric.HttpRequestCount.builder()
                    .withTag(CordaMetrics.Tag.Address, "$ctxMethod ${ctx.matchedPath()}")
                    .build().increment()
                val requestTimer = CordaMetrics.Metric.HttpRequestTime.builder()
                    .withTag(CordaMetrics.Tag.Address, "$ctxMethod ${ctx.matchedPath()}")
                    .build()
                requestTimer.recordCallable {
                    try {
                        validateRequestContentType(this, ctx)

                        val clientHttpRequestContext = ClientHttpRequestContext(ctx)
                        val paramValues = retrieveParameters(clientHttpRequestContext)

                        methodLogger.debug {
                            "Invoke method \"${method.method.name}\" with paramValues \"${
                                paramValues.joinToString(
                                    ","
                                )
                            }\"."
                        }

                        @Suppress("SpreadOperator")
                        val result = invokeDelegatedMethod(*paramValues.toTypedArray())

                        ctx.buildJsonResult(result, this.method.method.returnType)

                        ctx.header(Header.CACHE_CONTROL, "no-cache")
                        methodLogger.debug { "Invoke method \"${this.method.method.name}\" for route info completed." }
                    } catch (e: Exception) {
                        methodLogger.info("Error invoking path '${this.fullPath}' - ${e.message}")
                        throw HttpExceptionMapper.mapToResponse(e)
                    } finally {
                        if (ctx.isMultipartFormData()) {
                            cleanUpMultipartRequest(ctx)
                        }
                    }
                }
            }
        }
    }

    fun RouteInfo.retrieveParameters(ctx: ClientRequestContext): List<Any?> {
        val parametersRetrieverContext = ParametersRetrieverContext(ctx)
        val paramValues = parameters.map {
            val parameterRetriever = ParameterRetrieverFactory.create(it, this)
            parameterRetriever.apply(parametersRetrieverContext)
        }
        return paramValues
    }

    private fun cleanUpMultipartRequest(ctx: Context) {
        ctx.uploadedFiles().forEach { it.content.close() }
        // Remove all the parts and associated file storage once we are done with them
        ctx.req.parts.forEach { part ->
            try {
                part.delete()
            } catch (e: Exception) {
                log.warn("Could not delete part: ${part.name}", e)
            }
        }
    }

    fun authorize(authorizingSubject: AuthorizingSubject, resourceAccessString: String) {
        val principal = authorizingSubject.principal
        log.trace { "Authorize \"$principal\" for \"$resourceAccessString\"." }
        if (!authorizingSubject.isPermitted(resourceAccessString)) {
            val pathParts = resourceAccessString.split(METHOD_SEPARATOR, limit = 2)
            withMDC(principal, pathParts.firstOrNull() ?: "no_method", pathParts.lastOrNull() ?: "no_path") {
                "User not authorized.".let {
                    log.info(it)
                    throw ForbiddenResponse(it)
                }
            }
        }
        log.trace { "Authorize \"$principal\" for \"$resourceAccessString\" completed." }
    }
}
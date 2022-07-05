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
import net.corda.httprpc.server.impl.apigen.processing.RouteInfo
import net.corda.httprpc.server.impl.internal.HttpExceptionMapper
import net.corda.httprpc.server.impl.internal.ParameterRetrieverFactory
import net.corda.httprpc.server.impl.internal.ParametersRetrieverContext
import net.corda.httprpc.server.impl.security.HttpRpcSecurityManager
import net.corda.httprpc.server.impl.security.provider.credentials.CredentialResolver
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import javax.security.auth.login.FailedLoginException

internal object ContextUtils {

    private val log = LoggerFactory.getLogger(ContextUtils::class.java)

    const val contentTypeApplicationJson = "application/json"

    private const val CORDA_X500_NAME = "O=Http RPC Server, L=New York, C=US"

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
            log.info("Servicing ${ctx.method()} request to '${ctx.path()}")
            log.debug { "Invoke method \"${this.method.method.name}\" for route info." }
            log.trace { "Get parameter values." }
            try {
                validateRequestContentType(this, ctx)

                val clientHttpRequestContext = ClientHttpRequestContext(ctx)
                val paramValues = retrieveParameters(clientHttpRequestContext)

                log.debug { "Invoke method \"${method.method.name}\" with paramValues \"${paramValues.joinToString(",")}\"." }

                @Suppress("SpreadOperator")
                val result = invokeDelegatedMethod(*paramValues.toTypedArray())

                buildJsonResult(result, ctx, this)

                ctx.header(Header.CACHE_CONTROL, "no-cache")
                log.debug { "Invoke method \"${this.method.method.name}\" for route info completed." }
            } catch (e: Exception) {
                log.warn("Error invoking path '${this.fullPath}'.", e)
                throw HttpExceptionMapper.mapToResponse(e)
            } finally {
                if(ctx.isMultipartFormData()) {
                    cleanUpMultipartRequest(ctx)
                }
            }
        }
    }

    fun RouteInfo.retrieveParameters(ctx: ClientRequestContext): List<Any?> {
        val parametersRetrieverContext = ParametersRetrieverContext(ctx)
        val paramValues = parameters.map {
            val parameterRetriever = ParameterRetrieverFactory.create(it, this.isMultipartFileUpload)
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

    private fun buildJsonResult(result: Any?, ctx: Context, routeInfo: RouteInfo) {
        when {
            (result as? String) != null ->
                ctx.contentType(contentTypeApplicationJson).result(result)
            result != null ->
                ctx.json(result)
            else -> {
                // if the method has no return type we don't return null
                if (routeInfo.method.method.returnType != Void.TYPE) {
                    ctx.result("null")
                }
            }
        }
    }

    fun authorize(authorizingSubject: AuthorizingSubject, resourceAccessString: String) {
        val principal = authorizingSubject.principal
        log.trace { "Authorize \"$principal\" for \"$resourceAccessString\"." }
        if (!authorizingSubject.isPermitted(resourceAccessString))
            throw ForbiddenResponse("User not authorized.")
        log.trace { "Authorize \"$principal\" for \"$resourceAccessString\" completed." }
    }
}
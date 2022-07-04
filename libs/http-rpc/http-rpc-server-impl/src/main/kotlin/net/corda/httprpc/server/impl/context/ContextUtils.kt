package net.corda.httprpc.server.impl.context

import io.javalin.core.util.Header
import io.javalin.http.Context
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

    private fun Context.addHeaderValues(name: String, values: Iterable<String>) {
        values.forEach {
            this.res.addHeader(name, it)
        }
    }

    //https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/WWW-Authenticate
    //this allows the implementation of HTTP Digest or for example SPNEGO in the future
    private fun Context.addWwwAuthenticateHeaders(securityManager: HttpRpcSecurityManager) {
        val authMethods = securityManager.getSchemeProviders().map {
            val parameters = it.provideParameters()
            val attributes = if (parameters.isEmpty()) "" else {
                parameters.map { (k, v) -> "$k=\"$v\"" }.joinToString(", ")
            }
            "${it.authenticationMethod} $attributes"
        }

        addHeaderValues(Header.WWW_AUTHENTICATE, authMethods)
    }

    fun authenticate(ctx: Context, securityManager: HttpRpcSecurityManager, credentialResolver: CredentialResolver): AuthorizingSubject {
        log.trace { "Authenticate request." }
        log.debug { """Authenticate for path: "${ctx.path()}".""" }

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

    fun Context.getResourceAccessString(): String {
        val queryString = queryString()
        // Examples of strings will look like:
        // GET:/api/v1/permission/getpermission?id=c048679a-9654-4359-befc-9d2d22695a43
        // POST:/api/v1/user/createuser
        return method() + ":" + path() + if (!queryString.isNullOrBlank()) "?$queryString" else ""
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

    fun RouteInfo.invokeMethod(): (Context) -> Unit {
        return { ctx ->
            log.info("Servicing ${ctx.method()} request to '${ctx.path()}")
            log.debug { "Invoke method \"${this.method.method.name}\" for route info." }
            log.trace { "Get parameter values." }
            try {
                validateRequestContentType(this, ctx)

                val paramValues = retrieveParameters(ctx)

                log.debug { "Invoke method \"${method.method.name}\" with paramValues \"${paramValues.joinToString(",")}\"." }

                @Suppress("SpreadOperator")
                //TODO if one parameter is a list and it's exposed as a query parameter, we may need to cast list elements here
                val result = invokeDelegatedMethod(*paramValues)

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

    private fun RouteInfo.retrieveParameters(ctx: Context): Array<Any?> {
        val parametersRetrieverContext = ParametersRetrieverContext(ctx)
        val paramValues = parameters.map {
            val parameterRetriever = ParameterRetrieverFactory.create(it, this.isMultipartFileUpload)
            parameterRetriever.apply(parametersRetrieverContext)
        }.toTypedArray()
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
}
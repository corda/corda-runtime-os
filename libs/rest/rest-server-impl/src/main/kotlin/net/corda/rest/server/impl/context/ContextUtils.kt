package net.corda.rest.server.impl.context

import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.Header
import io.javalin.http.UnauthorizedResponse
import net.corda.data.rest.PasswordExpiryStatus
import net.corda.metrics.CordaMetrics
import net.corda.rest.authorization.AuthorizingSubject
import net.corda.rest.exception.HttpApiException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.security.Actor
import net.corda.rest.security.CURRENT_REST_CONTEXT
import net.corda.rest.security.InvocationContext
import net.corda.rest.security.RestAuthContext
import net.corda.rest.security.restContext
import net.corda.rest.server.impl.apigen.processing.RouteInfo
import net.corda.rest.server.impl.context.ClientRequestContext.Companion.METHOD_SEPARATOR
import net.corda.rest.server.impl.internal.HttpExceptionMapper
import net.corda.rest.server.impl.internal.ParameterRetrieverFactory
import net.corda.rest.server.impl.internal.ParametersRetrieverContext
import net.corda.rest.server.impl.security.RestAuthenticationProvider
import net.corda.rest.server.impl.security.provider.credentials.CredentialResolver
import net.corda.utilities.MDC_METHOD
import net.corda.utilities.MDC_PATH
import net.corda.utilities.MDC_USER
import net.corda.utilities.debug
import net.corda.utilities.trace
import net.corda.utilities.withMDC
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import javax.security.auth.login.FailedLoginException

internal object ContextUtils {

    private val log = LoggerFactory.getLogger(ContextUtils::class.java)

    const val contentTypeApplicationJson = "application/json"

    private const val CORDA_X500_NAME = "O=HTTP REST Server, L=New York, C=US"

    private fun <T> withMDC(user: String, method: String, path: String, block: () -> T): T {
        return withMDC(
            listOf(
                MDC_USER to user,
                MDC_METHOD to method,
                MDC_PATH to path
            ).toMap(),
            block
        )
    }

    private fun String.loggerFor(): Logger {
        return LoggerFactory.getLogger(ContextUtils::class.java.name + "." + this)
    }

    @Suppress("ThrowsCount")
    fun authenticate(
        ctx: ClientRequestContext,
        restAuthProvider: RestAuthenticationProvider,
        credentialResolver: CredentialResolver
    ): AuthorizingSubject {
        log.trace { "Authenticate request." }
        log.debug { """Authenticate for path: "${ctx.path}".""" }

        val credentials = credentialResolver.resolve(ctx)
            ?: """User credentials are empty or cannot be resolved""".let {
                log.info(it)
                ctx.addWwwAuthenticateHeaders(restAuthProvider)
                throw UnauthorizedResponse(it)
            }

        try {
            return restAuthProvider.authenticate(credentials).also {
                val restAuthContext = RestAuthContext(
                    InvocationContext(
                        Actor.service(
                            this::javaClass.toString(),
                            MemberX500Name.parse(CORDA_X500_NAME)
                        )
                    ),
                    it
                )
                if (it.expiryStatus == PasswordExpiryStatus.CLOSE_TO_EXPIRY) {
                    ctx.addPasswordExpiryHeader(it.expiryStatus)
                }

                CURRENT_REST_CONTEXT.set(restAuthContext)
                log.trace { """Authenticate user "${it.principal}" completed.""" }
            }
        } catch (e: FailedLoginException) {
            "Error during user authentication".let {
                log.warn("$it: ${e.message}")
                ctx.addWwwAuthenticateHeaders(restAuthProvider)
                throw UnauthorizedResponse(it)
            }
        }
    }

    private fun validateRequestContentType(routeInfo: RouteInfo, ctx: Context) {
        val expectsMultipart = routeInfo.isMultipartFileUpload
        val receivesMultipartRequest = ctx.isMultipart()

        if (expectsMultipart && !receivesMultipartRequest) {
            throw IllegalArgumentException("Endpoint expects Content-Type [multipart/form-data] but received [${ctx.contentType()}].")
        } else if (receivesMultipartRequest && !expectsMultipart) {
            throw IllegalArgumentException("Unexpected Content-Type [${ctx.contentType()}].")
        }
    }

    fun RouteInfo.invokeHttpMethod(): (Context) -> Unit {
        return { ctx ->
            val ctxMethod = ctx.method().name
            withMDC(restContext()?.principal ?: "<anonymous>", ctxMethod, ctx.path()) {
                val methodLogger = ctxMethod.loggerFor()
                methodLogger.info("Servicing $ctxMethod request to '${ctx.path()}' and invoking  method \"${method.method.name}\"")
                methodLogger.trace { "Get parameter values." }

                val startTime = System.nanoTime()
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
                    "Error invoking path '${this.fullPath}' - ${e.message}".let {
                        methodLogger.info(it)
                        methodLogger.debug(it, e)
                    }
                    throw HttpExceptionMapper.mapToResponse(e)
                } finally {
                    if (ctx.isMultipartFormData()) {
                        cleanUpMultipartRequest(ctx)
                    }

                    CordaMetrics.Metric.HttpRequestTime.builder()
                        .withTag(CordaMetrics.Tag.UriPath, ctx.matchedPath())
                        .withTag(CordaMetrics.Tag.HttpMethod, ctxMethod)
                        .withTag(CordaMetrics.Tag.OperationStatus, "${ctx.status()}")
                        .build().record(Duration.ofNanos(System.nanoTime() - startTime))
                }
            }
        }
    }

    @Suppress("ThrowsCount")
    fun RouteInfo.retrieveParameters(ctx: ClientRequestContext): List<Any?> {
        val parametersRetrieverContext = ParametersRetrieverContext(ctx)
        val paramValues = parameters.map { parameter ->
            val parameterRetriever = ParameterRetrieverFactory.create(parameter, this)
            try {
                parameterRetriever.apply(parametersRetrieverContext)
            } catch (ex: Exception) {
                throw buildBadRequestResponse("Unable to parse parameter '${parameter.name}'", ex)
            }
        }
        return paramValues
    }

    private fun buildBadRequestResponse(message: String, e: Exception): HttpApiException {
        return (e.cause as? HttpApiException) ?: InvalidInputDataException(message, buildExceptionCauseDetails(e))
    }

    /**
     * We'll add the name of the exception and the exception's message to the extra details map.
     * This will give the user extra information to resolving their issue.
     */
    internal fun buildExceptionCauseDetails(e: Exception) = mapOf(
        "cause" to e::class.java.simpleName,
        "reason" to (e.message ?: "")
    )

    private fun cleanUpMultipartRequest(ctx: Context) {
        ctx.uploadedFiles().forEach { it.content().close() }
        // Remove all the parts and associated file storage once we are done with them
        ctx.req().parts.forEach { part ->
            try {
                part.delete()
            } catch (e: Exception) {
                log.warn("Could not delete part: ${part.name}", e)
            }
        }
    }

    internal fun userNotAuthorized(user: String, resourceAccessString: String) {
        val pathParts = resourceAccessString.split(METHOD_SEPARATOR, limit = 2)
        withMDC(user, pathParts.firstOrNull() ?: "no_method", pathParts.lastOrNull() ?: "no_path") {
            "User not authorized.".let {
                log.info(it)
                throw ForbiddenResponse(it)
            }
        }
    }
}

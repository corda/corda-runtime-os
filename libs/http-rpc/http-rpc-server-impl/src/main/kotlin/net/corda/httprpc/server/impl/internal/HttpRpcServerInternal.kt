package net.corda.httprpc.server.impl.internal

import com.fasterxml.jackson.core.JsonProcessingException
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.core.util.Header
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.HttpResponseException
import io.javalin.http.HttpResponseExceptionMapper
import io.javalin.http.UnauthorizedResponse
import io.javalin.http.util.RedirectToLowercasePathPlugin
import io.javalin.plugin.json.JavalinJackson
import io.javalin.plugin.openapi.OpenApiOptions
import io.javalin.plugin.openapi.OpenApiPlugin
import io.javalin.plugin.openapi.ui.SwaggerOptions
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.OAuthFlow
import io.swagger.v3.oas.models.security.OAuthFlows
import io.swagger.v3.oas.models.security.Scopes
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import net.corda.httprpc.security.Actor
import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.security.InvocationContext
import net.corda.httprpc.security.RpcAuthContext
import net.corda.httprpc.server.config.HttpRpcSettingsProvider
import net.corda.httprpc.server.impl.exception.MissingParameterException
import net.corda.httprpc.server.impl.security.HttpRpcSecurityManager
import net.corda.httprpc.server.impl.security.provider.bearer.azuread.AzureAdAuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.credentials.DefaultCredentialResolver
import net.corda.httprpc.server.impl.utils.addHeaderValues
import net.corda.httprpc.server.impl.utils.executeWithThreadContextClassLoader
import net.corda.utilities.rootCause
import net.corda.utilities.rootMessage
import net.corda.v5.application.flows.BadRpcStartFlowRequestException
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.httprpc.Controller
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.http2.HTTP2Cipher
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import java.io.OutputStream
import java.io.PrintStream
import javax.security.auth.login.FailedLoginException
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

@Suppress("TooManyFunctions", "TooGenericExceptionThrown", "TooGenericExceptionCaught")
internal class HttpRpcServerInternal(
    private val securityManager: HttpRpcSecurityManager,
    private val configurationsProvider: HttpRpcSettingsProvider,
    private val controllers: List<Controller>
    ) {

    internal companion object {
        private val log = contextLogger()

        private const val contentTypeApplicationJson = "application/json"

        @VisibleForTesting
        internal const val SSL_PASSWORD_MISSING =
            "SSL key store password must be present in order to start a secure server"

        @VisibleForTesting
        internal const val INSECURE_SERVER_DEV_MODE_WARNING =
            "Creating insecure (HTTP) server is only permitted when using `devMode=true` in the node configuration."
        internal const val CORDA_X500_NAME = "O=Http RPC Server, L=New York, C=US"
        internal const val CONTENT_LENGTH_EXCEEEDS_LIMIT = "Content length is %d which exceeds the maximum limit of %d."
    }

    init {
        JavalinJackson.configure(serverJacksonObjectMapper)
    }

    private val credentialResolver = DefaultCredentialResolver()
    private val server = Javalin.create {
        it.registerPlugin(RedirectToLowercasePathPlugin())
        it.registerPlugin(getConfiguredOpenApiPlugin())
        val rendererBundle = FrameworkUtil.getBundle(SwaggerUIRenderer::class.java)
        // In an OSGi context, webjars cannot be loaded automatically using `JavalinConfig.enableWebJars`. We load
        // Swagger UI's static files manually instead.
        if (rendererBundle != null) {
            val swaggerUiBundle = rendererBundle
                .bundleContext
                .bundles
                .find { bundle -> bundle.symbolicName == OptionalDependency.SWAGGERUI.symbolicName }

            if (swaggerUiBundle != null) {
                val swaggerUiClassloader = swaggerUiBundle.adapt(BundleWiring::class.java).classLoader
                executeWithThreadContextClassLoader(swaggerUiClassloader) {
                    it.addStaticFiles("/META-INF/resources/")
                }
            }
        } else {
            it.enableWebjars()
        }

        if (log.isDebugEnabled) {
            it.enableDevLogging()
        }
        it.server {
            configurationsProvider.getSSLKeyStorePath()
                ?.let { createSecureServer() }
                ?: INSECURE_SERVER_DEV_MODE_WARNING.let { msg ->
                    if (configurationsProvider.isDevModeEnabled())
                        log.warn(msg)
                    else {
                        log.error(msg)
                        throw UnsupportedOperationException(msg)
                    }
                    createInsecureServer()
                }
        }
        it.defaultContentType = contentTypeApplicationJson
    }.apply {
        routes {
            path("${configurationsProvider.getBasePath()}/v${configurationsProvider.getApiVersion()}") {
                controllers.forEach(Controller::register)
            }
        }

        log.info("Adding max content checking to POST paths")
        log.info("Adding authorization to all paths")
        before {
            val httpVerb = it.method()
            val path = it.path()
            // Literal translation of existing code but applying content length to all paths might be acceptable
            if (httpVerb == "POST") {
                log.info("POST => $path - Checking max content length")
                if (it.contentLength() > configurationsProvider.maxContentLength()) throw BadRequestResponse(
                    CONTENT_LENGTH_EXCEEEDS_LIMIT.format(
                        it.contentLength(),
                        configurationsProvider.maxContentLength()
                    )
                )
            }
            if ("swagger" !in path) {
                log.info("$httpVerb => $path - Authorizing request")
                authorize(authenticate(it), path, httpVerb)
            }
        }

        // Intercept all exceptions and map to various responses/exception types
        exception(Exception::class.java) { e, ctx ->
            // handle general exceptions here
            // will not trigger if more specific exception-mapper found
            val message = """Error during invoking path "${ctx.path()}": ${e.rootMessage ?: e.rootCause}"""
            log.error(message, e)
            mapToResponse(ctx, message, e)
        }
    }

    fun getConfiguredOpenApiPlugin() = OpenApiPlugin(
        OpenApiOptions {
            // Use this method of setting up the OpenAPI options as it gives more customization
            OpenAPI().also { openApi ->
                openApi.info = Info().apply {
                    version(configurationsProvider.getApiVersion())
                    title(configurationsProvider.getApiTitle())
                    description(configurationsProvider.getApiDescription())
                    contact(Contact().apply {
                        name = "rpc team"
                        url = "rpc@r3.com"
                    })
                }
                controllers.map(Controller::tag).forEach(openApi::addTagsItem)
//                openApi.addServersItem(
//                    io.swagger.v3.oas.models.servers.Server().url(
//                        "/${configurationsProvider.getBasePath()}/v${configurationsProvider.getApiVersion()}".replace("/+".toRegex(), "/")
//                    )
//                )
                // The version above adds "/api/v1/" to each web request in the swagger ui when using "try it out"
                openApi.addServersItem(
                    io.swagger.v3.oas.models.servers.Server().url("/")
                )
                // Set up the authorization code
                openApi.components((openApi.components ?: Components()).apply {
                    addSecuritySchemes(
                        "basicAuth",
                        SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")
                    )
                    openApi.addSecurityItem(SecurityRequirement().addList("basicAuth"))
                    addAzureAdIfNecessary(openApi, this)
                })
            }
        }.apply {
            val pathForOpenApiUI = "${configurationsProvider.getBasePath()}/v${configurationsProvider.getApiVersion()}/swagger"
            val pathForOpenApiJson = "$pathForOpenApiUI.json"
            log.info("Running Swagger UI at /$pathForOpenApiUI")
            log.info("Hosting OpenAPI JSON at /$pathForOpenApiJson")
            path(pathForOpenApiJson) // endpoint for OpenAPI json
            swagger(SwaggerOptions(pathForOpenApiUI)) // endpoint for swagger-ui
//            path("/swagger-docs") // endpoint for OpenAPI json
//            swagger(SwaggerOptions("/swagger-ui")) // endpoint for swagger-ui
        }
    )

    private fun addAzureAdIfNecessary(openApi: OpenAPI, components: Components) {
        val azureAd = configurationsProvider.getSsoSettings()?.azureAd()
        if (azureAd != null) {
            components.addSecuritySchemes(
                "azuread", SecurityScheme()
                    .type(SecurityScheme.Type.OAUTH2)
                    .flows(
                        OAuthFlows()
                            .authorizationCode(
                                OAuthFlow()
                                .authorizationUrl(azureAd.getAuthorizeUrl())
                                .tokenUrl(azureAd.getTokenUrl())
                                .scopes(Scopes().apply {
                                    AzureAdAuthenticationProvider.SCOPE.split(' ').forEach { scope ->
                                        addString(scope, scope)
                                    }
                                })
                            )
                    )
                    .extensions(mapOf("x-tokenName" to "id_token"))
            )


            openApi.addSecurityItem(SecurityRequirement().addList("azuread", "AzureAd authentication"))
        }
    }

    //https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/WWW-Authenticate
    //this allows the implementation of HTTP Digest or for example SPNEGO in the future
    private fun addWwwAuthenticateHeaders(context: Context) {
        val authMethods = securityManager.getSchemeProviders().map {
            val parameters = it.provideParameters()
            val attributes = if (parameters.isEmpty()) "" else {
                parameters.map { (k, v) -> "$k=\"$v\"" }.joinToString(", ")
            }
            "${it.authenticationMethod} $attributes"
        }

        context.addHeaderValues(Header.WWW_AUTHENTICATE, authMethods)
    }

    private fun authenticate(ctx: Context): AuthorizingSubject {
        log.trace { "Authenticate request." }
        log.debug { """Authenticate for path: "${ctx.path()}".""" }

        val credentials = credentialResolver.resolve(ctx)
            ?: """User credentials are empty or cannot be resolved""".let {
                log.info(it)
                addWwwAuthenticateHeaders(ctx)
                throw UnauthorizedResponse(it)
            }

        try {
            return securityManager.authenticate(credentials).also {
                val rpcAuthContext = RpcAuthContext(
                    InvocationContext.Rpc(
                        Actor.service(
                            this::javaClass.toString(),
                            CordaX500Name.parse(CORDA_X500_NAME)
                        )
                    ), it
                )
                CURRENT_RPC_CONTEXT.set(rpcAuthContext)
                log.trace { """Authenticate user "${it.principal}" completed.""" }
            }
        } catch (e: FailedLoginException) {
            "Error during user authentication".let {
                log.warn("$it: ${e.message}")
                addWwwAuthenticateHeaders(ctx)
                throw UnauthorizedResponse(it)
            }
        }
    }

    // This changed from [methodFullName] to a [path] because there is no concept of a method name now.
    private fun authorize(authorizingSubject: AuthorizingSubject, path: String, httpVerb: String) {
        val principal = authorizingSubject.principal
        log.trace { "Authorize \"$principal\" for \"$path\"." }
        if (!authorizingSubject.isPermitted(path, httpVerb))
            throw ForbiddenResponse("Method \"$path\" not allowed for: \"$principal\".")
        log.trace { "Authorize \"$principal\" for \"$path\" completed." }
    }

    @Suppress("ThrowsCount", "ComplexMethod")
    private fun mapToResponse(ctx: Context, message: String, e: Exception) {
        val messageEscaped = message.replace("\n", " ")
        val mappedException = when (e) {
            is HttpResponseException -> e

            is BadRpcStartFlowRequestException -> BadRequestResponse(messageEscaped)
            is JsonProcessingException -> BadRequestResponse(messageEscaped)
//TODO restore these when possible
//            is StartFlowPermissionException -> throw ForbiddenResponse(messageEscaped)
//            is FlowNotFoundException -> throw NotFoundResponse(messageEscaped)
            is MissingParameterException -> BadRequestResponse(messageEscaped)
//            is InvalidCordaX500NameException -> throw BadRequestResponse(messageEscaped)
//            is MemberNotFoundException -> throw NotFoundResponse(messageEscaped)

            else -> {
                with(mutableMapOf<String, String>()) {
                    this["exception"] = e.toString()
                    this["rootCause"] = e.rootCause.toString()
                    e.rootMessage?.let { this["rootMessage"] = it }
                    HttpResponseException(HttpStatus.INTERNAL_SERVER_ERROR_500, messageEscaped, this)
                }
            }
        }
        // Call Javalin's exception message mapping code
        // The mapped exception must be of type [HttpResponseException]
        // Can't rethrow exceptions from this method as we are already in the exception handling code path
        HttpResponseExceptionMapper.handle(mappedException, ctx)
    }

    fun start() {
        val existingSystemErrStream = System.err
        try {
            log.trace { "Starting the Javalin server." }
            // Required because Javalin prints an error directly to stderr if it cannot find a logging implementation
            // via the service loader mechanism. This mechanism is not appropriate for OSGi. The logging
            // implementation is found correctly in practice.
            System.setErr(PrintStream(OutputStream.nullOutputStream()))

            // We need to set thread context classloader here as
            // `org.eclipse.jetty.websocket.servlet.WebSocketServletFactory.Loader.load` relies on it to perform
            // classloading during `start` method invocation.
            val bundle = FrameworkUtil.getBundle(WebSocketServletFactory::class.java)
            if (bundle != null) {
                bundle.loadClass(WebSocketServletFactory::class.java.name).classLoader.let { classLoader ->
                    executeWithThreadContextClassLoader(classLoader) {
                        server.start(configurationsProvider.getHostAndPort().host, configurationsProvider.getHostAndPort().port)
                    }
                }
            } else {
                server.start(configurationsProvider.getHostAndPort().host, configurationsProvider.getHostAndPort().port)
            }
            log.trace { "Starting the Javalin server completed." }
        } catch (e: Exception) {
            "Error when starting the Javalin server".let {
                log.error("$it: ${e.message}")
                throw Exception(it, e)
            }
        } finally {
            System.setErr(existingSystemErrStream)
        }
    }

    fun stop() {
        log.trace { "Stop the Javalin server." }
        server.stop()
        log.trace { "Stop the Javalin server completed." }
    }

    @SuppressWarnings("ComplexMethod")
    private fun createSecureServer(): Server {
        log.trace { "Create secure (HTTPS) server." }
        require(configurationsProvider.getSSLKeyStorePath() != null) {
            "SSL key store path must be present in order to start a secure server"
        }
        require(configurationsProvider.getSSLKeyStorePassword() != null) { SSL_PASSWORD_MISSING }

        log.trace { "Get SslContextFactory." }
        val sslContextFactory = SslContextFactory.Server().apply {
            keyStorePath = configurationsProvider.getSSLKeyStorePath()!!.toAbsolutePath().toString()
            setKeyStorePassword(configurationsProvider.getSSLKeyStorePassword()!!)
            cipherComparator = HTTP2Cipher.COMPARATOR
            provider = "Conscrypt"
        }
        log.trace { "Get SslConnectionFactory." }
        log.trace { "Get HttpConfiguration." }
        val httpsConfig = HttpConfiguration().apply {
            sendServerVersion = false
            secureScheme = "https"
            securePort = configurationsProvider.getHostAndPort().port
            addCustomizer(SecureRequestCustomizer())
        }

        val http11 = HttpConnectionFactory(httpsConfig)

        fun Server.addHttp11SslConnector() {
            val ssl = SslConnectionFactory(sslContextFactory, http11.protocol)
            addConnector(ServerConnector(this, ssl, http11).apply {
                port = configurationsProvider.getHostAndPort().port
            })
        }

        try {
            return Server().apply {
                /**
                 * HTTP2 connector currently disabled because:
                 * - There is no explicit requirement to support HTTP2
                 * - There are not that many HTTP clients exist in Java that support HTTP2
                 * - Fallback mechanism to HTTP 1.1 is not working as expected
                 */
                // addHttp2SslConnector()
                // HTTP/1.1 Connector
                addHttp11SslConnector()
            }.also { log.trace { "Create secure (HTTPS) server completed." } }
        } catch (e: Exception) {
            "Error during Create secure (HTTPS) server".let {
                log.error("$it: ${e.message}")
                throw Exception(it, e)
            }
        }
    }

    private fun createInsecureServer(): Server {
        try {
            log.trace { "Create insecure (HTTP) server." }

            return Server().apply {
                //HTTP/1.1 Connector
                addConnector(ServerConnector(this).apply {
                    port = configurationsProvider.getHostAndPort().port
                    host = configurationsProvider.getHostAndPort().host
                })
            }.also { log.trace { "Create insecure (HTTP) server completed." } }
        } catch (e: Exception) {
            "Error during Create insecure (HTTP) server".let {
                log.error("$it: ${e.message}")
                throw Exception(it, e)
            }
        }
    }
}

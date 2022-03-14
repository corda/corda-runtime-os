package net.corda.httprpc.server.impl.internal

import io.javalin.Javalin
import io.javalin.core.util.Header
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.HandlerType
import io.javalin.http.UnauthorizedResponse
import io.javalin.http.util.MultipartUtil
import io.javalin.http.util.RedirectToLowercasePathPlugin
import io.javalin.plugin.json.JavalinJackson
import net.corda.httprpc.security.Actor
import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.security.InvocationContext
import net.corda.httprpc.security.RpcAuthContext
import net.corda.httprpc.server.config.HttpRpcSettingsProvider
import net.corda.httprpc.server.impl.apigen.processing.RouteInfo
import net.corda.httprpc.server.impl.apigen.processing.RouteProvider
import net.corda.httprpc.server.impl.apigen.processing.openapi.OpenApiInfoProvider
import net.corda.httprpc.server.impl.security.HttpRpcSecurityManager
import net.corda.httprpc.server.impl.security.provider.credentials.DefaultCredentialResolver
import net.corda.httprpc.server.impl.utils.addHeaderValues
import net.corda.httprpc.server.impl.utils.executeWithThreadContextClassLoader
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
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
import javax.servlet.MultipartConfigElement

@Suppress("TooManyFunctions", "TooGenericExceptionThrown")
internal class HttpRpcServerInternal(
    private val resourceProvider: RouteProvider,
    private val securityManager: HttpRpcSecurityManager,
    private val configurationsProvider: HttpRpcSettingsProvider,
    private val openApiInfoProvider: OpenApiInfoProvider,

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
        internal const val CONTENT_LENGTH_EXCEEDS_LIMIT = "Content length is %d which exceeds the maximum limit of %d."
    }

    init {
        JavalinJackson.configure(serverJacksonObjectMapper)
    }

    private val credentialResolver = DefaultCredentialResolver()
    private val server = Javalin.create {
        it.registerPlugin(RedirectToLowercasePathPlugin())
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
        it.enableCorsForAllOrigins()
    }.apply {
        addRoutes()
        addOpenApiRoute()
        // In order for multipart content to be stored onto disk, we need to override some properties
        // which are set by default by Javalin such that entire content is read into memory
        MultipartUtil.preUploadFunction = { req ->
            req.setAttribute("org.eclipse.jetty.multipartConfig",
                MultipartConfigElement(
                    System.getProperty("java.io.tmpdir"),
                    configurationsProvider.maxContentLength().toLong(),
                    configurationsProvider.maxContentLength().toLong(),
                    1024))
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
                addWwwAuthenticateHeaders(ctx)
                throw UnauthorizedResponse(it)
            }
        }
    }

    private fun authorize(authorizingSubject: AuthorizingSubject, fullPath: String) {
        val principal = authorizingSubject.principal
        log.trace { "Authorize \"$principal\" for \"$fullPath\"." }
        if (!authorizingSubject.isPermitted(fullPath))
            throw ForbiddenResponse("User not authorized.")
        log.trace { "Authorize \"$principal\" for \"$fullPath\" completed." }
    }

    @SuppressWarnings("ComplexMethod", "ThrowsCount")
    private fun Javalin.addRoutes() {
        fun registerHandlerForRoute(routeInfo: RouteInfo, handlerType: HandlerType) {
            try {
                log.info("Add \"$handlerType\" handler for \"${routeInfo.fullPath}\".")
                // TODO the following hardcoded handler registration is only meant for Scaffold and needs change
                //  once "multipart/form-data" support gets implemented correctly as part of CORE-3813.
                if (routeInfo.fullPath == "/api/v1/cpi") {
                    addHandler(handlerType, routeInfo.fullPath, routeInfo.invokeMultiPartMethod())
                } else {
                    addHandler(handlerType, routeInfo.fullPath, routeInfo.invokeMethod())
                }
                log.debug { "Add \"$handlerType\" handler for \"${routeInfo.fullPath}\" completed." }
            } catch (e: Exception) {
                "Error during Add GET and POST routes".let {
                    log.error("$it: ${e.message}")
                    throw Exception(it, e)
                }
            }
        }
        try {
            log.trace { "Add GET and POST routes." }
            resourceProvider.httpNoAuthRequiredGetRoutes.map { routeInfo ->
                registerHandlerForRoute(routeInfo, HandlerType.GET)
            }
            resourceProvider.httpGetRoutes.map { routeInfo ->

                before(routeInfo.fullPath) {
                    authorize(authenticate(it), it.fullUrl())
                }
                registerHandlerForRoute(routeInfo, HandlerType.GET)
            }
            resourceProvider.httpPostRoutes.map { routeInfo ->
                before(routeInfo.fullPath) {
                    with(configurationsProvider.maxContentLength()) {
                        if (it.contentLength() > this) throw BadRequestResponse(
                            CONTENT_LENGTH_EXCEEDS_LIMIT.format(
                                it.contentLength(),
                                this
                            )
                        )
                    }
                    authorize(authenticate(it), it.fullUrl())
                }
                registerHandlerForRoute(routeInfo, HandlerType.POST)
            }
            log.trace { "Add GET and POST routes completed." }
        } catch (e: Exception) {
            "Error during Add GET and POST routes".let {
                log.error("$it: ${e.message}")
                throw Exception(it, e)
            }
        }
    }

    private fun Javalin.addOpenApiRoute() {
        try {
            log.trace { "Add OpenApi route." }
            get(openApiInfoProvider.pathForOpenApiJson)
            { ctx -> ctx.result(openApiInfoProvider.openApiString).contentType(contentTypeApplicationJson) }

            get(openApiInfoProvider.pathForOpenApiUI, openApiInfoProvider.swaggerUIRenderer)

            log.trace { "Add OpenApi route completed." }
        } catch (e: Exception) {
            "Error during Add OpenApi route".let {
                log.error("$it: ${e.message}")
                throw Exception(it, e)
            }
        }
    }

    private fun RouteInfo.invokeMethod(): (Context) -> Unit {
        return { ctx ->
            log.debug { "Invoke method \"${this.method.method.name}\" for route info." }
            log.trace { "Get parameter values." }
            try {
                val parametersRetrieverContext = ParametersRetrieverContext(ctx)
                val paramValues = parameters.map {
                    val parameterRetriever = ParameterRetrieverFactory.create(it)
                    parameterRetriever.apply(parametersRetrieverContext)
                }.toTypedArray()

                log.debug { "Invoke method \"${method.method.name}\" with paramValues \"${paramValues.joinToString(",")}\"." }

                @Suppress("SpreadOperator")
                //TODO if one parameter is a list and it's exposed as a query parameter, we may need to cast list elements here
                val result = invokeDelegatedMethod(*paramValues)
                if (result != null) {
                    ctx.json(result)
                }
                ctx.header(Header.CACHE_CONTROL, "no-cache")
                log.debug { "Invoke method \"${this.method.method.name}\" for route info completed." }
            } catch (e: Exception) {
                log.warn("Error invoking path '${this.fullPath}'.", e)
                throw HttpExceptionMapper.mapToResponse(e)
            }
        }
    }

    // TODO the following method should be integrated to the normal RPC handlers registering flow (i.e. `RouteInfo.invokeMethod`)
    private fun RouteInfo.invokeMultiPartMethod(): (Context) -> Unit {
        return { ctx ->
            try {
                // TODO uploadedFiles can be more than one
                val uploadedFile = ctx.uploadedFiles().single()
                val fileName = uploadedFile.filename
                val stream = uploadedFile.content
                val result = stream.use { invokeDelegatedMethod(fileName, it) }
                if (result != null) {
                    ctx.json(result)
                }
            } catch (e: Exception) {
                throw HttpExceptionMapper.mapToResponse(e)
            } finally {
                // Remove all the parts and associated file storage once we are done with them
                ctx.req.parts.forEach { part ->
                    try {
                        part.delete()
                    } catch (e: Exception) {
                        log.warn("Could not delete part: ${part.name}", e)
                    }
                }
            }
        }
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

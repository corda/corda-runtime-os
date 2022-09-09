package net.corda.httprpc.server.impl.internal

import io.javalin.Javalin
import io.javalin.core.util.Header
import io.javalin.http.BadRequestResponse
import io.javalin.http.ContentType
import io.javalin.http.HandlerType
import io.javalin.http.HttpResponseException
import io.javalin.http.staticfiles.Location
import io.javalin.http.util.JsonEscapeUtil
import io.javalin.http.util.MultipartUtil
import io.javalin.http.util.RedirectToLowercasePathPlugin
import io.javalin.plugin.json.JavalinJackson
import net.corda.httprpc.server.config.RestServerSettingsProvider
import net.corda.httprpc.server.impl.apigen.processing.RouteInfo
import net.corda.httprpc.server.impl.apigen.processing.RouteProvider
import net.corda.httprpc.server.impl.apigen.processing.openapi.OpenApiInfoProvider
import net.corda.httprpc.server.impl.context.ClientHttpRequestContext
import net.corda.httprpc.server.impl.security.RestAuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.credentials.DefaultCredentialResolver
import net.corda.httprpc.server.impl.context.ContextUtils.authenticate
import net.corda.httprpc.server.impl.context.ContextUtils.authorize
import net.corda.httprpc.server.impl.context.ContextUtils.contentTypeApplicationJson
import net.corda.httprpc.server.impl.context.ContextUtils.invokeHttpMethod
import net.corda.utilities.classload.executeWithThreadContextClassLoader
import net.corda.utilities.classload.OsgiClassLoader
import net.corda.utilities.executeWithStdErrSuppressed
import net.corda.utilities.VisibleForTesting
import org.slf4j.LoggerFactory
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
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import java.nio.file.Path
import javax.servlet.MultipartConfigElement
import net.corda.httprpc.server.impl.websocket.WebSocketCloserService
import net.corda.httprpc.server.impl.websocket.mapToWsStatusCode
import java.util.LinkedList

@Suppress("TooManyFunctions", "TooGenericExceptionThrown", "LongParameterList")
internal class RestServerInternal(
    private val resourceProvider: RouteProvider,
    private val restAuthProvider: RestAuthenticationProvider,
    private val configurationsProvider: RestServerSettingsProvider,
    private val openApiInfoProvider: OpenApiInfoProvider,
    multiPartDir: Path,
    private val webSocketCloserService: WebSocketCloserService
) {

    internal companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        @VisibleForTesting
        internal const val SSL_PASSWORD_MISSING =
            "SSL key store password must be present in order to start a secure server"

        @VisibleForTesting
        internal const val INSECURE_SERVER_DEV_MODE_WARNING =
            "Creating insecure (HTTP) server is only permitted when using `devMode=true` in the node configuration."
        internal const val CONTENT_LENGTH_EXCEEDS_LIMIT = "Content length is %d which exceeds the maximum limit of %d."
    }

    private val webSocketRouteAdaptors = LinkedList<AutoCloseable>()
    private val credentialResolver = DefaultCredentialResolver()
    private val server = Javalin.create {
        it.jsonMapper(JavalinJackson(serverJacksonObjectMapper))
        it.registerPlugin(RedirectToLowercasePathPlugin())

        val swaggerUiBundle = getSwaggerUiBundle()
        // In an OSGi context, webjars cannot be loaded automatically using `JavalinConfig.enableWebJars`.
        // We instruct loading Swagger UI static files manually instead.
        // Note: `addStaticFiles` perform a check that resource does exist.
        // The actual loading of resources though is happening at `start()` time below.
        if (swaggerUiBundle != null) {
            val swaggerUiClassloader = swaggerUiBundle.adapt(BundleWiring::class.java).classLoader
            executeWithThreadContextClassLoader(swaggerUiClassloader) {
                it.addStaticFiles("/META-INF/resources/", Location.CLASSPATH)
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
        addWsRoutes()
        // In order for multipart content to be stored onto disk, we need to override some properties
        // which are set by default by Javalin such that entire content is read into memory
        MultipartUtil.preUploadFunction = { req ->
            req.setAttribute("org.eclipse.jetty.multipartConfig",
                MultipartConfigElement(
                    multiPartDir.toString(),
                    configurationsProvider.maxContentLength().toLong(),
                    configurationsProvider.maxContentLength().toLong(),
                    1024))
        }
    }

    private fun addExceptionHandlers(app: Javalin) {

        app.exception(HttpResponseException::class.java) { e, ctx ->
            if (ctx.header(Header.ACCEPT)?.contains(ContentType.JSON) == true || ctx.res.contentType == ContentType.JSON) {
                ctx.status(e.status).result("""{
                |    "title": "${e.message?.let { JsonEscapeUtil.escape(it) }}",
                |    "status": ${e.status},
                |    "details": {${e.details.map { """"${it.key}":"${JsonEscapeUtil.escape(it.value)}"""" }.joinToString(",")}}
                |}""".trimMargin()
                ).contentType(ContentType.APPLICATION_JSON)
            } else {
                val result = if (e.details.isEmpty()) "${e.message}" else """
                |${e.message}
                |${
                    e.details.map {
                        """
                |${it.key}:
                |${it.value}
                |"""
                    }.joinToString("")
                }""".trimMargin()
                ctx.status(e.status).result(result)
            }
        }
    }

    internal val port: Int get() = server.port()

    private fun getSwaggerUiBundle(): Bundle? {
        val rendererBundle = FrameworkUtil.getBundle(SwaggerUIRenderer::class.java) ?: return null
        return rendererBundle
            .bundleContext
            .bundles
            .find { bundle -> bundle.symbolicName == OptionalDependency.SWAGGERUI.symbolicName }
    }

    @SuppressWarnings("ComplexMethod", "ThrowsCount")
    private fun Javalin.addRoutes() {

        try {
            log.trace { "Add routes by method." }
            // It is important to add no authentication get routes first such that
            // handler for GET "testEntity/getprotocolversion" will be found before GET "testEntity/:id" handler.
            resourceProvider.httpNoAuthRequiredGetRoutes.map { routeInfo ->
                registerHandlerForRoute(routeInfo, HandlerType.GET)
            }

            resourceProvider.httpGetRoutes.map { routeInfo ->
                before(routeInfo.fullPath) {
                    // Make an additional check that the path is not exempt from permissions check.
                    // This is necessary due to "before" matching logic cannot tell path "testEntity/:id" from
                    // "testEntity/getprotocolversion" and mistakenly finds "before" handler where there should be none.
                    // Javalin provides no way for modifying "before" handler finding logic.
                    if (resourceProvider.httpNoAuthRequiredGetRoutes.none { routeInfo -> routeInfo.fullPath == it.path() } &&
                            it.method() == "GET") {
                        val clientHttpRequestContext = ClientHttpRequestContext(it)
                        val authorizingSubject = authenticate(clientHttpRequestContext, restAuthProvider, credentialResolver)
                        authorize(authorizingSubject, clientHttpRequestContext.getResourceAccessString())
                    } else {
                        log.debug { "Call to ${it.path()} for method ${it.method()} identified as an exempt from authorization check." }
                    }
                }
                registerHandlerForRoute(routeInfo, HandlerType.GET)
            }

            addRouteWithContentLengthRestriction(resourceProvider.httpPostRoutes, HandlerType.POST)

            addRouteWithContentLengthRestriction(resourceProvider.httpPutRoutes, HandlerType.PUT)

            addRouteWithContentLengthRestriction(resourceProvider.httpDeleteRoutes, HandlerType.DELETE)

            log.trace { "Add routes by method completed." }
        } catch (e: Exception) {
            "Error during Add GET and POST routes".let {
                log.error("$it: ${e.message}")
                throw Exception(it, e)
            }
        }
    }

    private fun Javalin.registerHandlerForRoute(routeInfo: RouteInfo, handlerType: HandlerType) {
        try {
            log.trace("Add \"$handlerType\" handler for \"${routeInfo.fullPath}\".")

            addHandler(handlerType, routeInfo.fullPath, routeInfo.invokeHttpMethod())

            log.debug { "Add \"$handlerType\" handler for \"${routeInfo.fullPath}\" completed." }
        } catch (e: Exception) {
            "Error during adding routes".let {
                log.error("$it: ${e.message}")
                throw Exception(it, e)
            }
        }
    }

    private fun Javalin.addRouteWithContentLengthRestriction(routes: List<RouteInfo>, handlerType: HandlerType) {
        routes.map { routeInfo ->
            before(routeInfo.fullPath) {
                // For "before" handlers we have a global space of handlers in Javalin regardless of which method was actually
                // used. In case when two separate handlers created for GET and for DELETE for the same resource, without "if"
                // condition below both handlers will be used - which will be redundant.
                if (it.method() == handlerType.name) {
                    with(configurationsProvider.maxContentLength()) {
                        if (it.contentLength() > this) throw BadRequestResponse(
                            CONTENT_LENGTH_EXCEEDS_LIMIT.format(
                                it.contentLength(),
                                this
                            )
                        )
                    }
                    val clientHttpRequestContext = ClientHttpRequestContext(it)
                    val authorizingSubject = authenticate(clientHttpRequestContext, restAuthProvider, credentialResolver)
                    authorize(authorizingSubject, clientHttpRequestContext.getResourceAccessString())
                }
            }
            registerHandlerForRoute(routeInfo, handlerType)
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

    fun start() {
        val existingSystemErrStream = System.err
        try {
            log.trace { "Starting the Javalin server." }

            val bundle = FrameworkUtil.getBundle(WebSocketServletFactory::class.java)
            if (bundle != null) {
                val bundleList = listOfNotNull(bundle, getSwaggerUiBundle())
                val osgiClassLoader = OsgiClassLoader(bundleList)
                // We need to set thread context classloader at start time as
                // `org.eclipse.jetty.websocket.servlet.WebSocketServletFactory.Loader.load` relies on it to perform
                // classloading during `start` method invocation.
                executeWithThreadContextClassLoader(osgiClassLoader) {
                    // Required because Javalin prints an error directly to stderr if it cannot find a logging
                    // implementation via standard class loading mechanism. This mechanism is not appropriate for OSGi.
                    // The logging implementation is found correctly in practice.
                    executeWithStdErrSuppressed {
                        server.start(
                            configurationsProvider.getHostAndPort().host,
                            configurationsProvider.getHostAndPort().port
                        )
                    }
                }
            } else {
                server.start(configurationsProvider.getHostAndPort().host, configurationsProvider.getHostAndPort().port)
            }
            addExceptionHandlers(server)
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
        log.trace { "Close ${webSocketRouteAdaptors.size} WebSocket route adaptors." }
        webSocketRouteAdaptors.forEach { it.close() }
        log.trace { "Finished closing WebSocket route adaptors." }
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
                host = configurationsProvider.getHostAndPort().host
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

    private fun Javalin.addWsRoutes() {
        try {
            log.trace { "Add WebSockets routes for some of the GET methods" }

            resourceProvider.httpDuplexRoutes.map { routeInfo ->
                registerWsHandlerForRoute(routeInfo)
            }

            wsException(Exception::class.java) { e, ctx ->
                log.warn("Exception handled from WebSocket:", e)
                webSocketCloserService.close(ctx, e.mapToWsStatusCode())
            }

            log.trace { "Add WebSockets routes for some of the GET methods." }
        } catch (e: Exception) {
            "Error during Add WebSockets routes for some of the GET methods".let {
                log.error("$it: ${e.message}")
                throw Exception(it, e)
            }
        }
    }

    private fun Javalin.registerWsHandlerForRoute(routeInfo: RouteInfo) {
        try {
            log.info("Add WS handler for \"${routeInfo.fullPath}\".")

            ws(
                routeInfo.fullPath,
                routeInfo.setupWsCall(
                    restAuthProvider,
                    credentialResolver,
                    webSocketCloserService,
                    webSocketRouteAdaptors,
                    configurationsProvider.getWebSocketIdleTimeoutMs()
                )
            )

            log.debug { "Add WS handler for \"${routeInfo.fullPath}\" completed." }
        } catch (e: Exception) {
            "Error during adding WS routes".let {
                log.error("$it: ${e.message}")
                throw Exception(it, e)
            }
        }
    }
}
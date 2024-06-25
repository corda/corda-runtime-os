package net.corda.rest.server.impl.internal

import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.ContentType
import io.javalin.http.Context
import io.javalin.http.HandlerType
import io.javalin.http.Header
import io.javalin.http.HttpResponseException
import io.javalin.http.NotFoundResponse
import io.javalin.http.staticfiles.Location
import io.javalin.http.util.JsonEscapeUtil
import io.javalin.http.util.MultipartUtil
import io.javalin.json.JavalinJackson
import io.javalin.plugin.bundled.CorsPlugin
import io.javalin.plugin.bundled.RedirectToLowercasePathPlugin
import jakarta.servlet.MultipartConfigElement
import java.nio.file.Path
import java.util.LinkedList
import net.corda.rest.authorization.AuthorizationUtils.authorize
import net.corda.rest.server.config.RestServerSettingsProvider
import net.corda.rest.server.impl.apigen.processing.RouteInfo
import net.corda.rest.server.impl.apigen.processing.RouteProvider
import net.corda.rest.server.impl.apigen.processing.openapi.OpenApiInfoProvider
import net.corda.rest.server.impl.context.ClientHttpRequestContext
import net.corda.rest.server.impl.context.ContextUtils.authenticate
import net.corda.rest.server.impl.context.ContextUtils.contentTypeApplicationJson
import net.corda.rest.server.impl.context.ContextUtils.invokeHttpMethod
import net.corda.rest.server.impl.context.ContextUtils.userNotAuthorized
import net.corda.rest.server.impl.security.RestAuthenticationProvider
import net.corda.rest.server.impl.security.provider.credentials.DefaultCredentialResolver
import net.corda.rest.server.impl.websocket.WebSocketCloserService
import net.corda.rest.server.impl.websocket.mapToWsStatusCode
import net.corda.tracing.configureJavalinForTracing
import net.corda.utilities.VisibleForTesting
import net.corda.utilities.classload.executeWithThreadContextClassLoader
import net.corda.utilities.debug
import net.corda.utilities.trace
import net.corda.web.server.JavalinStarter
import org.eclipse.jetty.http2.HTTP2Cipher
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions", "TooGenericExceptionThrown", "LongParameterList")
internal class RestServerInternal(
    private val resourceProvider: RouteProvider,
    private val restAuthProvider: RestAuthenticationProvider,
    private val configurationsProvider: RestServerSettingsProvider,
    // Different OpenAPI providers for different versions
    private val openApiInfoProviders: List<OpenApiInfoProvider>,
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

    private lateinit var server: Javalin
    private val serverFactory: () -> Javalin = {
        Javalin.create { config ->
            config.jsonMapper(JavalinJackson(serverJacksonObjectMapper))
            config.registerPlugin(RedirectToLowercasePathPlugin())
            configureJavalinForTracing(config)

            val swaggerUiBundle = getSwaggerUiBundle()
            // In an OSGi context, webjars cannot be loaded automatically using `JavalinConfig.enableWebJars`.
            // We instruct loading Swagger UI static files manually instead.
            // Note: `addStaticFiles` perform a check that resource does exist.
            // The actual loading of resources though is happening at `start()` time below.
            if (swaggerUiBundle != null) {
                val swaggerUiClassloader = swaggerUiBundle.adapt(BundleWiring::class.java).classLoader
                executeWithThreadContextClassLoader(swaggerUiClassloader) {
                    config.staticFiles.add("/META-INF/resources/", Location.CLASSPATH)
                }
            } else {
                config.staticFiles.enableWebjars()
            }

            if (log.isDebugEnabled) {
                config.bundledPlugins.enableDevLogging()
            }

            if (configurationsProvider.getSSLKeyStorePath() != null) {
                createSecureServer(config)
            } else {
                if (configurationsProvider.isDevModeEnabled()) {
                    log.warn(INSECURE_SERVER_DEV_MODE_WARNING)
                } else {
                    log.error(INSECURE_SERVER_DEV_MODE_WARNING)
                    throw UnsupportedOperationException(INSECURE_SERVER_DEV_MODE_WARNING)
                }
                createInsecureServer(config)
            }

            config.http.defaultContentType = contentTypeApplicationJson
//            config.bundledPlugins.enableCors { cors ->
//                cors.addRule { corsConfig ->
//                    corsConfig.anyHost()
//                    corsConfig.exposeHeader(ACCESS_CONTROL_ALLOW_ORIGIN)
//                }
//            }

            config.registerPlugin(
                CorsPlugin { cors ->
                    cors.addRule { it.anyHost() }
                }
            )
        }.apply {
            addRoutes()
            addOpenApiRoutes()
            addWsRoutes()
            // In order for multipart content to be stored onto disk, we need to override some properties
            // which are set by default by Javalin such that entire content is read into memory
            @Suppress("DEPRECATION")
            MultipartUtil.preUploadFunction = { req ->
                req.setAttribute(
                    "org.eclipse.jetty.multipartConfig",
                    MultipartConfigElement(
                        multiPartDir.toString(),
                        configurationsProvider.maxContentLength().toLong(),
                        configurationsProvider.maxContentLength().toLong(),
                        1024
                    )
                )
            }
        }
    }

    private fun addExceptionHandlers(app: Javalin) {
        app.exception(NotFoundResponse::class.java) { e, ctx ->
            val detailsWithUrl = e.details.plus("url" to ctx.req().requestURL.toString())
            commonResult(NotFoundResponse(details = detailsWithUrl), ctx)
        }
        app.exception(HttpResponseException::class.java) { e, ctx ->
            commonResult(e, ctx)
        }
    }

    private fun commonResult(e: HttpResponseException, ctx: Context) {
        if (ctx.header(Header.ACCEPT)?.contains(ContentType.JSON) == true || ctx.res().contentType == ContentType.JSON) {
            ctx.status(e.status).result(
                """{
                |    "title": "${e.message?.let { JsonEscapeUtil.escape(it) }}",
                |    "status": ${e.status},
                |    "details": {${e.details.map { """"${it.key}":"${JsonEscapeUtil.escape(it.value)}"""" }.joinToString(",")}}
                |}
                """.trimMargin()
            ).contentType(ContentType.APPLICATION_JSON)
        } else {
            val result = if (e.details.isEmpty()) {
                "${e.message}"
            } else {
                """
                |${e.message}
                |${
                    e.details.map {
                        """
                |${it.key}:
                |${it.value}
                |"""
                    }.joinToString("")
                }
                """.trimMargin()
            }
            ctx.status(e.status).result(result)
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
                        it.method().name == "GET"
                    ) {
                        val clientHttpRequestContext = ClientHttpRequestContext(it)
                        val authorizingSubject = authenticate(clientHttpRequestContext, restAuthProvider, credentialResolver)
                        val authorizationProvider = routeInfo.method.instance.authorizationProvider
                        val resourceAccessString = clientHttpRequestContext.getResourceAccessString()

                        if (!authorize(authorizingSubject, resourceAccessString, authorizationProvider)) {
                            userNotAuthorized(authorizingSubject.principal, resourceAccessString)
                        }
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
            addHttpHandler(handlerType, routeInfo.fullPath, routeInfo.invokeHttpMethod())
            log.info("Added \"$handlerType\" handler for \"${routeInfo.fullPath}\".")
        } catch (e: Exception) {
            "Error during adding route. Handler type=$handlerType, Path=\"${routeInfo.fullPath}\"".let {
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
                if (it.method().name == handlerType.name) {
                    with(configurationsProvider.maxContentLength()) {
                        if (it.contentLength() > this) {
                            throw BadRequestResponse(
                                CONTENT_LENGTH_EXCEEDS_LIMIT.format(
                                    it.contentLength(),
                                    this
                                )
                            )
                        }
                    }
                    val clientHttpRequestContext = ClientHttpRequestContext(it)
                    val authorizingSubject =
                        authenticate(clientHttpRequestContext, restAuthProvider, credentialResolver)
                    val authorizationProvider = routeInfo.method.instance.authorizationProvider
                    val resourceAccessString = clientHttpRequestContext.getResourceAccessString()

                    if (!authorize(authorizingSubject, resourceAccessString, authorizationProvider)) {
                        userNotAuthorized(authorizingSubject.principal, resourceAccessString)
                    }
                }
            }
            registerHandlerForRoute(routeInfo, handlerType)
        }
    }

    private fun Javalin.addOpenApiRoutes() {
        try {
            log.trace { "Add OpenApi route." }
            openApiInfoProviders.forEach { openApiInfoProvider ->
                get(
                    openApiInfoProvider.pathForOpenApiJson
                ) { ctx -> ctx.result(openApiInfoProvider.openApiString).contentType(contentTypeApplicationJson) }
                get(openApiInfoProvider.pathForOpenApiUI, openApiInfoProvider.swaggerUIRenderer)
            }
            log.trace { "Add OpenApi route completed." }
        } catch (e: Exception) {
            "Error during Add OpenApi route".let {
                log.error("$it: ${e.message}")
                throw Exception(it, e)
            }
        }
    }

    fun start() {
        server = JavalinStarter.startServer(
            "REST API",
            serverFactory,
            configurationsProvider.getHostAndPort().port,
            configurationsProvider.getHostAndPort().host,
            getSwaggerUiBundle()?.let { listOf(it) } ?: emptyList()
        )
        addExceptionHandlers(server)
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
    private fun createSecureServer(config: JavalinConfig) {
        log.trace { "Create secure (HTTPS) server." }
        require(configurationsProvider.getSSLKeyStorePath() != null) {
            "SSL key store path must be present in order to start a secure server"
        }
        require(configurationsProvider.getSSLKeyStorePassword() != null) { SSL_PASSWORD_MISSING }

        config.jetty.modifyHttpConfiguration { httpConfig ->
            httpConfig.sendServerVersion = false
            httpConfig.secureScheme = "https"
            httpConfig.securePort = configurationsProvider.getHostAndPort().port
            httpConfig.addCustomizer(SecureRequestCustomizer().apply {
                isSniHostCheck = false
            })
        }

        config.jetty.addConnector() { server, httpConfig ->
            log.trace { "Get SslContextFactory." }
            val sslContextFactory = SslContextFactory.Server().apply {
                keyStorePath = configurationsProvider.getSSLKeyStorePath()!!.toAbsolutePath().toString()
                setKeyStorePassword(configurationsProvider.getSSLKeyStorePassword()!!)
                cipherComparator = HTTP2Cipher.COMPARATOR
            }


            val http11 = HttpConnectionFactory(httpConfig)
            val ssl = SslConnectionFactory(sslContextFactory, http11.protocol)

            ServerConnector(server, ssl, http11).apply {
                port = configurationsProvider.getHostAndPort().port
                host = configurationsProvider.getHostAndPort().host
            }
        }
        log.trace { "Create secure (HTTPS) server completed." }
    }

    private fun createInsecureServer(config: JavalinConfig) {
        log.trace { "Create insecure (HTTP) server." }

        config.jetty.addConnector() { server, _ ->
            ServerConnector(server).apply {
                port = configurationsProvider.getHostAndPort().port
                host = configurationsProvider.getHostAndPort().host
            }
        }

        log.trace { "Create insecure (HTTP) server." }
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

            log.debug { "Added WS handler for \"${routeInfo.fullPath}\"." }
        } catch (e: Exception) {
            "Error during adding WS route. Path=\"${routeInfo.fullPath}\"".let {
                log.error("$it: ${e.message}")
                throw Exception(it, e)
            }
        }
    }
}

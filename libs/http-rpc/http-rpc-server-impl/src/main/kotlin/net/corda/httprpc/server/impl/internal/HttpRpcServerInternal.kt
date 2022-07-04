package net.corda.httprpc.server.impl.internal

import io.javalin.Javalin
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.HandlerType
import io.javalin.http.staticfiles.Location
import io.javalin.http.util.MultipartUtil
import io.javalin.http.util.RedirectToLowercasePathPlugin
import io.javalin.plugin.json.JavalinJackson
import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.server.config.HttpRpcSettingsProvider
import net.corda.httprpc.server.impl.apigen.processing.RouteInfo
import net.corda.httprpc.server.impl.apigen.processing.RouteProvider
import net.corda.httprpc.server.impl.apigen.processing.openapi.OpenApiInfoProvider
import net.corda.httprpc.server.impl.security.HttpRpcSecurityManager
import net.corda.httprpc.server.impl.security.provider.credentials.DefaultCredentialResolver
import net.corda.httprpc.server.impl.context.ContextUtils.authenticate
import net.corda.httprpc.server.impl.context.ContextUtils.contentTypeApplicationJson
import net.corda.httprpc.server.impl.context.ContextUtils.getResourceAccessString
import net.corda.httprpc.server.impl.context.ContextUtils.invokeHttpMethod
import net.corda.httprpc.server.impl.utils.executeWithThreadContextClassLoader
import net.corda.utilities.classload.OsgiClassLoader
import net.corda.v5.base.annotations.VisibleForTesting
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
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import javax.servlet.MultipartConfigElement

@Suppress("TooManyFunctions", "TooGenericExceptionThrown")
internal class HttpRpcServerInternal(
    private val resourceProvider: RouteProvider,
    private val securityManager: HttpRpcSecurityManager,
    private val configurationsProvider: HttpRpcSettingsProvider,
    private val openApiInfoProvider: OpenApiInfoProvider,
    multiPartDir: Path
) {

    internal companion object {
        private val log = contextLogger()

        @VisibleForTesting
        internal const val SSL_PASSWORD_MISSING =
            "SSL key store password must be present in order to start a secure server"

        @VisibleForTesting
        internal const val INSECURE_SERVER_DEV_MODE_WARNING =
            "Creating insecure (HTTP) server is only permitted when using `devMode=true` in the node configuration."
        internal const val CONTENT_LENGTH_EXCEEDS_LIMIT = "Content length is %d which exceeds the maximum limit of %d."
    }

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

    private fun getSwaggerUiBundle(): Bundle? {
        val rendererBundle = FrameworkUtil.getBundle(SwaggerUIRenderer::class.java) ?: return null
        return rendererBundle
            .bundleContext
            .bundles
            .find { bundle -> bundle.symbolicName == OptionalDependency.SWAGGERUI.symbolicName }
    }





    private fun authorize(authorizingSubject: AuthorizingSubject, resourceAccessString: String) {
        val principal = authorizingSubject.principal
        log.trace { "Authorize \"$principal\" for \"$resourceAccessString\"." }
        if (!authorizingSubject.isPermitted(resourceAccessString))
            throw ForbiddenResponse("User not authorized.")
        log.trace { "Authorize \"$principal\" for \"$resourceAccessString\" completed." }
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
                        authorize(authenticate(it, securityManager, credentialResolver), it.getResourceAccessString())
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
            log.info("Add \"$handlerType\" handler for \"${routeInfo.fullPath}\".")

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
                if(it.method() == handlerType.name) {
                    with(configurationsProvider.maxContentLength()) {
                        if (it.contentLength() > this) throw BadRequestResponse(
                            CONTENT_LENGTH_EXCEEDS_LIMIT.format(
                                it.contentLength(),
                                this
                            )
                        )
                    }
                    authorize(authenticate(it, securityManager, credentialResolver), it.getResourceAccessString())
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
            // Required because Javalin prints an error directly to stderr if it cannot find a logging implementation
            // via the service loader mechanism. This mechanism is not appropriate for OSGi. The logging
            // implementation is found correctly in practice.
            System.setErr(PrintStream(OutputStream.nullOutputStream()))

            // We need to set thread context classloader here as
            // `org.eclipse.jetty.websocket.servlet.WebSocketServletFactory.Loader.load` relies on it to perform
            // classloading during `start` method invocation.
            val bundle = FrameworkUtil.getBundle(WebSocketServletFactory::class.java)
            if (bundle != null) {
                val bundleList = listOfNotNull(bundle, getSwaggerUiBundle())
                val osgiClassLoader = OsgiClassLoader(bundleList)
                // Correct context classloader need to be set at start time
                executeWithThreadContextClassLoader(osgiClassLoader) {
                    server.start(configurationsProvider.getHostAndPort().host, configurationsProvider.getHostAndPort().port)
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

            ws(routeInfo.fullPath, routeInfo.setupWsCall())

            log.debug { "Add WS handler for \"${routeInfo.fullPath}\" completed." }
        } catch (e: Exception) {
            "Error during adding WS routes".let {
                log.error("$it: ${e.message}")
                throw Exception(it, e)
            }
        }
    }
}
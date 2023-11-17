package net.corda.web.server

import io.javalin.Javalin
import io.javalin.http.NotFoundResponse
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import net.corda.tracing.configureJavalinForTracing
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebServer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component(service = [WebServer::class])
class JavalinServer(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val javalinFactory: () -> Javalin,
    platformInfoProvider: PlatformInfoProvider,
) : WebServer {
    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = PlatformInfoProvider::class)
        platformInfoProvider: PlatformInfoProvider,
    ) : this(
        coordinatorFactory,
        ::createJavalin,
        platformInfoProvider)

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        fun createJavalin(maxRequestSize: Long = 100_000_000L) =
            Javalin.create { config ->
                // hardcode to 100Mb for now
                // TODO CORE-17986: make configurable
                config.maxRequestSize = maxRequestSize

                if (log.isDebugEnabled) {
                    config.enableDevLogging()
                }
                configureJavalinForTracing(config)
            }
    }

    private val apiPathPrefix: String = "/api/${platformInfoProvider.localWorkerSoftwareShortVersion}"
    private var server: Javalin? = null
    private val coordinator = coordinatorFactory.createCoordinator<WebServer> { _, _ -> }
    private val serverLock = ReentrantLock()

    override val endpoints: MutableSet<Endpoint> = mutableSetOf()

    override fun start(port: Int) {
        serverLock.withLock {
            check(null == server) { "The Javalin webserver is already initialized" }
            coordinator.start()
            startServer(port)
        }
    }

    private fun startServer(port: Int) {
        // Ensure the server can only be started once at a time.
            log.info("Starting Worker Web Server on port: $port")
            server = javalinFactory()
            endpoints.forEach {
                registerEndpointInternal(it)
            }

            JavalinStarter.startServer(
                "RPC Server",
                server!!,
                port,
            )

            server?.events {
                it.handlerAdded { meta ->
                    log.info("Handler added to webserver: $meta")
                }
            }
            server?.exception(NotFoundResponse::class.java) { _, ctx ->
                log.warn("Received request on non-existing endpoint: ${ctx.req.requestURI}")
                ctx.result("404 Not Found")
                ctx.status(404)
            }
            coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }

    private fun restartServer() {
        serverLock.withLock {
            // restart server without marking the component down.
            checkNotNull(server) { "Cannot restart a non-existing server" }
            val port = server?.port()
            stopServer()
            checkNotNull(port) { "Required port is null" }
            startServer(port)
        }
    }

    override fun stop() {
        serverLock.withLock {
            coordinator.updateStatus(LifecycleStatus.DOWN)
            stopServer()
            coordinator.stop()
        }
    }

    override fun registerEndpoint(endpoint: Endpoint) {
        serverLock.withLock {
            if (endpoints.any { it.path == endpoint.path && it.methodType == endpoint.methodType })
                throw IllegalArgumentException("Endpoint with path ${endpoint.path} and method ${endpoint.methodType} already exists.")
            // register immediately when the server has been started
            if (null != server) registerEndpointInternal(endpoint)
            // record the path in case we need to register when it's already started
            endpoints.add(endpoint)
        }
    }

    override fun removeEndpoint(endpoint: Endpoint) {
        serverLock.withLock {
            endpoints.remove(endpoint)
            // NOTE:
            //  The server needs to be restarted to un-register the path. However, this means everything dependent on
            //  this is impacted by a restart, which doesn't feel quite right.
            //  This also means we can't really DOWN/UP the lifecycle status of this because this would end up in a
            //  relentless yoyo-ing of this component as dependent components keep calling this function.
            // TODO - review if it is really needed to de-register a path when a Subscription goes down, for example.
            if (null != server) restartServer()
        }
    }

    private fun registerEndpointInternal(endpoint: Endpoint) {
        checkNotNull(server) { "The Javalin webserver has not been initialized" }
        val path = if (endpoint.isApi) apiPathPrefix + endpoint.path else endpoint.path
        when (endpoint.methodType) {
            HTTPMethod.GET -> server?.get(path) { endpoint.webHandler.handle(JavalinContext(it)) }
            HTTPMethod.POST -> server?.post(path) { endpoint.webHandler.handle(JavalinContext(it)) }
        }
    }

    override val port: Int? get() = server?.port()
}
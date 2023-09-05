package net.corda.web.server

import io.javalin.Javalin
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import net.corda.utilities.classload.executeWithThreadContextClassLoader
import net.corda.utilities.executeWithStdErrSuppressed
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebServer
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory


@Component(service = [WebServer::class])
class JavalinServer(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val javalinFactory: () -> Javalin
) : WebServer {
    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory
    ) : this(coordinatorFactory, { Javalin.create() })

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var server: Javalin? = null
    private val coordinator = coordinatorFactory.createCoordinator<WebServer> { _, _ -> }
    private val endpoints: MutableList<Endpoint> = mutableListOf()

    override fun start(port: Int) {
        if (server != null) {
            throw CordaRuntimeException("The Javalin webserver is already initialized")
        }
        coordinator.start()

        try {
            log.debug("Starting Worker Web Server on port: $port")
            server = javalinFactory()
            startServer(port)

            endpoints.forEach {
                registerEndpointInternal(it)
            }

        } catch (ex: Exception) {
            throw CordaRuntimeException(ex.message, ex)
        }
    }

    private fun startServer(port: Int) {
        val bundle = FrameworkUtil.getBundle(WebSocketServletFactory::class.java)

        if (bundle == null) {
            server?.start(port)
        } else {
            // We temporarily switch the context class loader to allow Javalin to find `WebSocketServletFactory`.
            executeWithThreadContextClassLoader(bundle.adapt(BundleWiring::class.java).classLoader) {
                // Required because Javalin prints an error directly to stderr if it cannot find a logging
                // implementation via standard class loading mechanism. This mechanism is not appropriate for OSGi.
                // The logging implementation is found correctly in practice.
                executeWithStdErrSuppressed {
                    server?.start(port)
                }
            }
        }
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    override fun stop() {
        coordinator.updateStatus(LifecycleStatus.DOWN)
        server?.stop()
        server = null
        coordinator.stop()
    }

    override fun registerEndpoint(endpoint: Endpoint) {
        registerEndpointInternal(endpoint)
        endpoints.add(endpoint)
    }

    override fun removeEndpoint(endpoint: Endpoint) {
        requireServerInitialized()
        endpoints.remove(endpoint)
        stop()
        port?.let { startServer(it) }
    }

    private fun registerEndpointInternal(endpoint: Endpoint) {
        endpoint.validate()
        requireServerInitialized()
        when (endpoint.methodType) {
            HTTPMethod.GET -> server?.get(endpoint.endpoint) { endpoint.webHandler.handle(JavalinContext(it)) }
            HTTPMethod.POST -> server?.post(endpoint.endpoint) { endpoint.webHandler.handle(JavalinContext(it)) }
        }
    }

    override val port: Int? get() = server?.port()

    private fun requireServerInitialized() {
        if (server == null) {
            throw CordaRuntimeException("The Javalin webserver has not been initialized")
        }
    }
}
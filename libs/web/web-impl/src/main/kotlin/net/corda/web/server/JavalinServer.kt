package net.corda.web.server

import io.javalin.Javalin
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import net.corda.utilities.classload.executeWithThreadContextClassLoader
import net.corda.utilities.executeWithStdErrSuppressed
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
        check(null == server) { "The Javalin webserver is already initialized" }
        coordinator.start()
        startServer(port)
    }

    private fun startServer(port: Int) {
        log.info("Starting Worker Web Server on port: $port")
        server = javalinFactory()
        endpoints.forEach {
            registerEndpointInternal(it)
        }

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
        server?.events {
            it.handlerAdded { meta ->
                log.info("Handler added to webserver: $meta")
            }
        }
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }

    private fun restartServer() {
        // restart server without marking the component down.
        val port = server?.port()?:throw java.lang.IllegalStateException("Cannot restart a non-existing server")
        stopServer()
        startServer(port)
    }

    override fun stop() {
        coordinator.updateStatus(LifecycleStatus.DOWN)
        stopServer()
        coordinator.stop()
    }

    override fun registerEndpoint(endpoint: Endpoint) {
        // register immediately when the server has been started
        if(null != server) registerEndpointInternal(endpoint)
        // record the endpoint in case we need to register when it's already started
        endpoints.add(endpoint)
    }

    override fun removeEndpoint(endpoint: Endpoint) {
        if(null != server) endpoints.remove(endpoint)
        // NOTE: this is a bit crappy.
        //  The server needs to be restarted to un-register the endpoint. However, this means everything dependent on
        //  this is impacted by a restart, which doesn't feel right.
        //  This also means we can't really DOWN/UP the lifecycle status of this because this would end up in a
        //  relentless yoyo-ing of this component as dependent components keen calling this function.
        // TODO - review if it is really needed to de-register an endpoint when a Subscription goes down, for example.
        restartServer()
    }

    private fun registerEndpointInternal(endpoint: Endpoint) {
        checkNotNull(server) { "The Javalin webserver has not been initialized" }
        endpoint.validate()
        when (endpoint.methodType) {
            HTTPMethod.GET -> server?.get(endpoint.endpoint) { endpoint.webHandler.handle(JavalinContext(it)) }
            HTTPMethod.POST -> server?.post(endpoint.endpoint) { endpoint.webHandler.handle(JavalinContext(it)) }
        }
    }

    override val port: Int? get() = server?.port()
}
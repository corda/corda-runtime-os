package net.corda.web.server

import io.javalin.Javalin
import java.lang.StringBuilder
import java.net.MalformedURLException
import java.net.URL
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.WebContext
import net.corda.utilities.classload.executeWithThreadContextClassLoader
import net.corda.utilities.executeWithStdErrSuppressed
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory


@Component(service = [WebServer::class])
class JavalinServer @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = JavalinFactory::class)
    private val javalinFactory: JavalinFactory
): WebServer {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var server: Javalin? = null
    private val coordinator = coordinatorFactory.createCoordinator<WebServer> { _, _ -> }


    override fun start(port: Int) {
        if(server != null) {
            log.error("The Javalin webserver is already initialized")
            throw CordaRuntimeException("The Javalin webserver is already initialized")
        }
        coordinator.start()

        try {
            log.debug("Starting Worker Web Server on port: $port")
            server = javalinFactory.create()
            startServer(port)
        } catch (ex: Exception) {
            throw CordaRuntimeException("Webserver already active on that port")
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
        throwIfNull()
        coordinator.updateStatus(LifecycleStatus.DOWN)
        server?.stop()
        coordinator.stop()
    }

    override fun registerHandler(methodType: HTTPMethod, endpoint: String, handle: (WebContext) -> WebContext) {
        validateEndpoint(endpoint)
        throwIfNull()
        when (methodType) {
            HTTPMethod.GET -> server?.get(endpoint) { handle(JavalinContext(it)) }
            HTTPMethod.POST -> server?.post(endpoint) { handle(JavalinContext(it)) }
        }
    }

    override val port get() = server?.port()

    private fun throwIfNull() {
        if (server == null) {
            throw CordaRuntimeException("The Javalin webserver has not been initialized")
        }
    }

    private fun validateEndpoint(endpoint: String){

        val error = StringBuilder()

        if(endpoint.isBlank() || endpoint.isEmpty()) error.appendLine("Endpoint must not be null or empty")
        if(!endpoint.startsWith("/")) error.appendLine("Endpoint $endpoint must start with '/'")
        if(!isValidEndpoint(endpoint)) error.appendLine("Endpoint $endpoint is not validly formed")

        if(error.isNotEmpty()) {
            throw CordaRuntimeException(error.toString())
        }
    }

    fun isValidEndpoint(endpoint: String): Boolean {
        return try {
            // it will check only for scheme and not null input
            URL("http://test$endpoint")
            true
        } catch (e: MalformedURLException) {
            false
        }
    }
}
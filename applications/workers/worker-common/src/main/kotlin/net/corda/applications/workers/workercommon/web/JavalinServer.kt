package net.corda.applications.workers.workercommon.web

import io.javalin.Javalin
import net.corda.utilities.classload.executeWithThreadContextClassLoader
import net.corda.utilities.executeWithStdErrSuppressed
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

@Component(service = [WorkerWebServer::class])
class JavalinServer() : WorkerWebServer {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private lateinit var server: Javalin


    override fun listen(port: Int) {
        log.debug("Starting Worker Web Server on port: $port")
        server = Javalin
            .create()
            .apply { start(port) }
    }

    private fun start(port: Int) {
        val bundle = FrameworkUtil.getBundle(WebSocketServletFactory::class.java)

        if (bundle == null) {
            server.start(port)
        } else {
            // We temporarily switch the context class loader to allow Javalin to find `WebSocketServletFactory`.
            executeWithThreadContextClassLoader(bundle.adapt(BundleWiring::class.java).classLoader) {
                // Required because Javalin prints an error directly to stderr if it cannot find a logging
                // implementation via standard class loading mechanism. This mechanism is not appropriate for OSGi.
                // The logging implementation is found correctly in practice.
                executeWithStdErrSuppressed {
                    server.start(port)
                }
            }
        }
    }

    override fun stop() {
        throwIfUninitialized()
        server.stop()
    }

    override fun get(endpoint: String, handle: (WebContext) -> WebContext) {
        server.get(endpoint) {
            handle(JavalinContext(it))
        }
    }

    override fun post(endpoint: String, handle: (WebContext) -> WebContext) {
        server.post(endpoint) {
            handle(JavalinContext(it))
        }
    }

    override val port get() = server.port()

    private fun throwIfUninitialized() {
        if (!this::server.isInitialized) {
            throw CordaRuntimeException("The Javalin webserver has not been initialized")
        }
    }
}
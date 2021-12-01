package net.corda.applications.workers.healthprovider.internal

import io.javalin.Javalin
import net.corda.applications.workers.healthprovider.HTTP_HEALTH_PROVIDER
import net.corda.applications.workers.healthprovider.HealthProvider
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Component

/**
 * With this health provider, the worker is considered healthy if and only if an HTTP request to [HTTP_HEALTH_ROUTE]
 * returns a 200 code.
 *
 * In the same way, an HTTP request to [HTTP_READINESS_ROUTE] is used to ascertain worker readiness.
 *
 * The worker starts in an unhealthy, not-ready state.
 */
// TODO - Joel - Handle spew regarding log4j.
@Component(service = [HealthProvider::class], property = [HTTP_HEALTH_PROVIDER])
@Suppress("Unused")
internal class HttpBasedHealthProvider : HealthProvider {
    private var isHealthy = false
    private var isReady = false

    init {
        val app = Javalin.create()

        startServer(app)

        app.get(HTTP_HEALTH_ROUTE) { context ->
            val status = if (isHealthy) HTTP_OK_CODE else HTTP_INTERNAL_SERVER_ERROR_CODE
            context.status(status)
        }

        app.get(HTTP_READINESS_ROUTE) { context ->
            val status = if (isReady) HTTP_OK_CODE else HTTP_INTERNAL_SERVER_ERROR_CODE
            context.status(status)
        }
    }

    override fun setHealthy() {
        isHealthy = true
    }

    override fun setNotHealthy() {
        isHealthy = false
    }

    override fun setReady() {
        isReady = true
    }

    override fun setNotReady() {
        isReady = false
    }

    /** Starts a Javalin server on port [HTTP_HEALTH_PROVIDER_PORT]. */
    private fun startServer(server: Javalin) {
        val bundle = FrameworkUtil.getBundle(WebSocketServletFactory::class.java)

        if (bundle == null) {
            server.start(HTTP_HEALTH_PROVIDER_PORT)
        } else {
            // We temporarily switch the context class loader to allow Javalin to find `WebSocketServletFactory`.
            val factoryClassLoader = bundle.loadClass(WebSocketServletFactory::class.java.name).classLoader
            val threadClassLoader = Thread.currentThread().contextClassLoader
            try {
                Thread.currentThread().contextClassLoader = factoryClassLoader
                server.start(HTTP_HEALTH_PROVIDER_PORT)
            } finally {
                Thread.currentThread().contextClassLoader = threadClassLoader
            }
        }
    }
}
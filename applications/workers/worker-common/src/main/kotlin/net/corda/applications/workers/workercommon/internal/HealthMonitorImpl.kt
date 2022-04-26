package net.corda.applications.workers.workercommon.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.Javalin
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * An implementation of [HealthMonitor].
 *
 * @property server The server that serves worker health and readiness.
 */
@Component(service = [HealthMonitor::class])
@Suppress("Unused")
internal class HealthMonitorImpl @Activate constructor(
    @Reference(service = LifecycleRegistry::class)
    private val lifecycleRegistry: LifecycleRegistry
) : HealthMonitor {
    // The use of Javalin is temporary, and will be replaced in the future.
    private var server: Javalin? = null
    private val objectMapper = ObjectMapper()

    override fun listen(port: Int) {
        server = Javalin
            .create()
            .apply { startServer(this, port) }
            .get(HTTP_HEALTH_ROUTE) { context ->
                val anyComponentsUnhealthy = existsComponentWithAnyOf(setOf(LifecycleStatus.ERROR))
                val status = if (anyComponentsUnhealthy) HTTP_SERVICE_UNAVAILABLE_CODE else HTTP_OK_CODE
                context.status(status)
            }
            .get(HTTP_STATUS_ROUTE) { context ->
                val anyComponentsNotReady = existsComponentWithAnyOf(setOf(LifecycleStatus.DOWN, LifecycleStatus.ERROR))
                val status = if (anyComponentsNotReady) HTTP_SERVICE_UNAVAILABLE_CODE else HTTP_OK_CODE
                context.status(status)
                // Print as text so to avoid bringing in a JSON serialiser
                // TODO - should we return json?
                context.result(objectMapper.writeValueAsString(lifecycleRegistry.componentStatus()))
            }
    }

    override fun stop() {
        server?.stop()
    }

    /** Starts a Javalin server on [port]. */
    private fun startServer(server: Javalin, port: Int) {
        val bundle = FrameworkUtil.getBundle(WebSocketServletFactory::class.java)

        if (bundle == null) {
            server.start(port)
        } else {
            // We temporarily switch the context class loader to allow Javalin to find `WebSocketServletFactory`.
            val factoryClassLoader = bundle.loadClass(WebSocketServletFactory::class.java.name).classLoader
            val threadClassLoader = Thread.currentThread().contextClassLoader
            try {
                Thread.currentThread().contextClassLoader = factoryClassLoader
                server.start(port)
            } finally {
                Thread.currentThread().contextClassLoader = threadClassLoader
            }
        }
    }

    /** Indicates whether any components exist with at least one of the given [statuses]. */
    private fun existsComponentWithAnyOf(statuses: Iterable<LifecycleStatus>) =
        lifecycleRegistry.componentStatus().entries.any { coordinatorStatus ->
            coordinatorStatus.value.status in statuses
        }
}
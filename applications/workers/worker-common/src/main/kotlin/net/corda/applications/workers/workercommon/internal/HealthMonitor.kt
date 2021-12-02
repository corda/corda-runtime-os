package net.corda.applications.workers.workercommon.internal

import io.javalin.Javalin
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

// TODO - Joel - Force workers to load this component. Currently, the component isn't loaded.
// TODO - Joel - Handle spew about log4j on server startup.

/**
 * Monitors the health of a worker.
 *
 * A worker indicates its healthiness/readiness by returning a 200 code for HTTP requests to
 * [HTTP_HEALTH_ROUTE]/[HTTP_READINESS_ROUTE].
 *
 * A worker is considered healthy if no component has a [LifecycleStatus] of [LifecycleStatus.ERROR]. A worker is
 * considered ready if no component has a [LifecycleStatus] of either [LifecycleStatus.DOWN] or [LifecycleStatus.ERROR].
 */
@Component(service = [HealthMonitor::class])
@Suppress("Unused")
internal class HealthMonitor @Activate constructor(
    @Reference(service = LifecycleRegistry::class)
    private val lifecycleRegistry: LifecycleRegistry
) {

    init {
        Javalin
            .create()
            .apply { startServer(this) }
            .get(HTTP_HEALTH_ROUTE) { context ->
                val anyComponentsUnhealthy = existsComponentWithAnyOf(setOf(LifecycleStatus.ERROR))
                val status = if (anyComponentsUnhealthy) HTTP_INTERNAL_SERVER_ERROR_CODE else HTTP_OK_CODE
                context.status(status)
            }
            .get(HTTP_READINESS_ROUTE) { context ->
                val anyComponentsNotReady = existsComponentWithAnyOf(setOf(LifecycleStatus.DOWN, LifecycleStatus.ERROR))
                val status = if (anyComponentsNotReady) HTTP_INTERNAL_SERVER_ERROR_CODE else HTTP_OK_CODE
                context.status(status)
            }
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

    /** Indicates whether any components exist with at least one of the given [statuses]. */
    private fun existsComponentWithAnyOf(statuses: Iterable<LifecycleStatus>) =
        lifecycleRegistry.componentStatus().entries.any { coordinatorStatus ->
            coordinatorStatus.value.status in statuses
        }
}
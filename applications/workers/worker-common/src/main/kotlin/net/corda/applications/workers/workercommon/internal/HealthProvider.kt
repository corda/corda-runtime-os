package net.corda.applications.workers.workercommon.internal

import io.javalin.Javalin
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

// TODO - Joel - Force re-inclusion of this provider by workers. Currently, the component isn't loaded.

/**
 * A worker is considered healthy if and only if an HTTP request to [HTTP_HEALTH_ROUTE] returns a 200 code.
 *
 * In the same way, an HTTP request to [HTTP_READINESS_ROUTE] is used to ascertain worker readiness.
 *
 * // TODO - Joel - Explain how health/readiness is ascertained (i.e. based on components).
 */
// TODO - Joel - Handle spew regarding log4j.
@Component(service = [HealthProvider::class])
@Suppress("Unused")
internal class HealthProvider @Activate constructor(
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
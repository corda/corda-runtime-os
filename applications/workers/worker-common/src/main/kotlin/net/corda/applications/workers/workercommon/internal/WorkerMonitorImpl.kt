package net.corda.applications.workers.workercommon.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.Javalin
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.metrics.CordaMetrics
import net.corda.utilities.classload.OsgiClassLoader
import net.corda.utilities.classload.executeWithThreadContextClassLoader
import net.corda.utilities.executeWithStdErrSuppressed
import net.corda.v5.base.util.contextLogger
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * An implementation of [WorkerMonitor].
 *
 * @property server The server that serves worker health and readiness.
 */
@Component(service = [WorkerMonitor::class])
@Suppress("Unused")
internal class WorkerMonitorImpl @Activate constructor(
    @Reference(service = LifecycleRegistry::class)
    private val lifecycleRegistry: LifecycleRegistry
) : WorkerMonitor {
    private companion object {
        val logger = contextLogger()
    }

    // The use of Javalin is temporary, and will be replaced in the future.
    private var server: Javalin? = null
    private val objectMapper = ObjectMapper()
    private val prometheusRegistry: PrometheusMeterRegistry

    init {
        logger.info("Creating Prometheus metric registry")
        prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        CordaMetrics.configure("TODO-INSERT-TYPE-OF-WORKER", prometheusRegistry)

        ClassLoaderMetrics().bindTo(CordaMetrics.registry)
        JvmMemoryMetrics().bindTo(CordaMetrics.registry)
        JvmGcMetrics().bindTo(CordaMetrics.registry)
        ProcessorMetrics().bindTo(CordaMetrics.registry)
        JvmThreadMetrics().bindTo(CordaMetrics.registry)
        UptimeMetrics().bindTo(CordaMetrics.registry)
    }


    override fun listen(port: Int) {
        server = Javalin
            .create()
            .apply { startServer(this, port) }
            .get(HTTP_HEALTH_ROUTE) { context ->
                val unhealthyComponents = componentWithStatus(setOf(LifecycleStatus.ERROR))
                val status = if (unhealthyComponents.isEmpty()) {
                    HTTP_OK_CODE
                } else {
                    logger.warn("Status is unhealthy. The status of $unhealthyComponents has error.")
                    HTTP_SERVICE_UNAVAILABLE_CODE
                }
                context.status(status)
            }
            .get(HTTP_STATUS_ROUTE) { context ->
                val anyComponentsNotReady = componentWithStatus(setOf(LifecycleStatus.DOWN, LifecycleStatus.ERROR))
                    .isNotEmpty()
                val status = if (anyComponentsNotReady) HTTP_SERVICE_UNAVAILABLE_CODE else HTTP_OK_CODE
                context.status(status)
                context.result(objectMapper.writeValueAsString(lifecycleRegistry.componentStatus()))
            }
            .get(HTTP_METRICS_ROUTE) { context ->
                context.result(prometheusRegistry.scrape())
            }
    }

    override val port get() = server?.port()

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
            executeWithThreadContextClassLoader(OsgiClassLoader(listOf(bundle))) {
                // Required because Javalin prints an error directly to stderr if it cannot find a logging
                // implementation via standard class loading mechanism. This mechanism is not appropriate for OSGi.
                // The logging implementation is found correctly in practice.
                executeWithStdErrSuppressed {
                    server.start(port)
                }
            }
        }
    }

    /** Indicates whether any components exist with at least one of the given [statuses]. */
    private fun componentWithStatus(statuses: Collection<LifecycleStatus>) =
        lifecycleRegistry.componentStatus().values.filter { coordinatorStatus ->
            statuses.contains(coordinatorStatus.status)
        }.map {
            it.name
        }
}
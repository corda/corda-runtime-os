package net.corda.applications.workers.workercommon.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.core.util.Header
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.metrics.CordaMetrics
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebContext
import net.corda.web.api.WebHandler
import net.corda.web.api.WebServer

/**
 * An implementation of [WorkerMonitor].
 *
 * @property server The server that serves worker health and readiness.
 */
@Component(service = [WorkerMonitor::class])
@Suppress("Unused")
internal class WorkerMonitorImpl @Activate constructor(
    @Reference(service = LifecycleRegistry::class)
    private val lifecycleRegistry: LifecycleRegistry,
    @Reference(service = WebServer::class)
    private val webServer: WebServer
) : WorkerMonitor {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val objectMapper = ObjectMapper()
    private val prometheusRegistry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val lastLogMessage = ConcurrentHashMap(mapOf(HTTP_HEALTH_ROUTE to "", HTTP_STATUS_ROUTE to ""))

    private fun setupMetrics(name: String) {
        logger.info("Creating Prometheus metric registry")
        CordaMetrics.configure(name, prometheusRegistry)

        ClassLoaderMetrics().bindTo(CordaMetrics.registry)
        JvmMemoryMetrics().bindTo(CordaMetrics.registry)
        JvmGcMetrics().bindTo(CordaMetrics.registry)
        JvmHeapPressureMetrics().bindTo(CordaMetrics.registry)
        ProcessorMetrics().bindTo(CordaMetrics.registry)
        JvmThreadMetrics().bindTo(CordaMetrics.registry)
        UptimeMetrics().bindTo(CordaMetrics.registry)
        FileDescriptorMetrics().bindTo(CordaMetrics.registry)
    }


    override fun registerEndpoints(workerType: String) {
        setupMetrics(workerType)

        val healthRoutehandler = object : WebHandler {
            override fun handle(context: WebContext): WebContext {
                val unhealthyComponents = componentWithStatus(setOf(LifecycleStatus.ERROR))
                val status = if (unhealthyComponents.isEmpty()) {
                    clearLastLogMessageForRoute(HTTP_HEALTH_ROUTE)
                    HTTP_OK_CODE
                } else {
                    logIfDifferentFromLastMessage(
                        HTTP_HEALTH_ROUTE,
                        "Status is unhealthy. The status of $unhealthyComponents has error."
                    )
                    HTTP_SERVICE_UNAVAILABLE_CODE
                }
                context.status(status)
                context.header(Header.CACHE_CONTROL, NO_CACHE)
                return context
            }
        }

        val statusRouteHandler = object : WebHandler {
            override fun handle(context: WebContext): WebContext {
                val notReadyComponents = componentWithStatus(setOf(LifecycleStatus.DOWN, LifecycleStatus.ERROR))
                val status = if (notReadyComponents.isEmpty()) {
                    clearLastLogMessageForRoute(HTTP_STATUS_ROUTE)
                    HTTP_OK_CODE
                } else {
                    logIfDifferentFromLastMessage(
                        HTTP_STATUS_ROUTE,
                        "There are components with error or down state: $notReadyComponents."
                    )
                    HTTP_SERVICE_UNAVAILABLE_CODE
                }
                context.status(status)
                context.result(objectMapper.writeValueAsString(lifecycleRegistry.componentStatus()))
                context.header(Header.CACHE_CONTROL, NO_CACHE)
                return context
            }
        }

        val metricsRouteHandler = object : WebHandler {
            override fun handle(context: WebContext): WebContext {
                context.result(prometheusRegistry.scrape())
                context.header(Header.CACHE_CONTROL, NO_CACHE)
                return context
            }

        }

        webServer.registerEndpoint(Endpoint(HTTPMethod.GET, HTTP_HEALTH_ROUTE, healthRoutehandler))
        webServer.registerEndpoint(Endpoint(HTTPMethod.GET, HTTP_STATUS_ROUTE, statusRouteHandler))
        webServer.registerEndpoint(Endpoint(HTTPMethod.GET, HTTP_METRICS_ROUTE, metricsRouteHandler))
    }

    private fun clearLastLogMessageForRoute(route: String) {
        lastLogMessage[route] = ""
    }

    private fun logIfDifferentFromLastMessage(route: String, logMessage: String) {
        val lastLogMessage = lastLogMessage.put(route, logMessage)
        if (logMessage != lastLogMessage) {
            logger.warn(logMessage)
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
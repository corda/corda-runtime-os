package net.corda.applications.workers.workercommon.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.core.util.Header
import io.micrometer.cloudwatch2.CloudWatchConfig
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry
import io.micrometer.core.instrument.Clock
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
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import java.util.concurrent.ConcurrentHashMap
import net.corda.rest.ResponseCode
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebHandler
import net.corda.web.api.WebServer

/**
 * An implementation of [WorkerMonitor].
 *
 * @property webServer The server that serves worker health and readiness.
 */
@Component(service = [WorkerMonitor::class])
@Suppress("Unused")
internal class WorkerMonitorImpl @Activate constructor(
    @Reference(service = LifecycleRegistry::class)
    private val lifecycleRegistry: LifecycleRegistry,
    @Reference(service = WebServer::class)
    private val webServer: WebServer
) : WorkerMonitor {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CORDA_NAMESPACE = "CORDA"
        private const val K8S_NAMESPACE_KEY = "K8S_NAMESPACE"
        private const val CLOUDWATCH_ENABLED_KEY = "ENABLE_CLOUDWATCH"
    }

    private val objectMapper = ObjectMapper()
    private val prometheusRegistry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val cloudwatchConfig = object : CloudWatchConfig {

        override fun get(key: String): String? {
            return null
        }

        override fun namespace(): String {
            val suffix = System.getenv(K8S_NAMESPACE_KEY)?.let {
                "/$it"
            } ?: ""
            return "$CORDA_NAMESPACE$suffix"
        }
    }
    private val lastLogMessage = ConcurrentHashMap(mapOf(HTTP_HEALTH_ROUTE to "", HTTP_STATUS_ROUTE to ""))

    private fun setupMetrics(name: String) {
        logger.info("Creating Prometheus metric registry")
        CordaMetrics.configure(name, prometheusRegistry)
        if (System.getenv(CLOUDWATCH_ENABLED_KEY) == "true") {
            logger.info("Enabling the cloudwatch metrics registry")
            val cloudwatchClient = CloudWatchAsyncClient.builder()
                .credentialsProvider(WebIdentityTokenFileCredentialsProvider.create())
                .build()
            CordaMetrics.configure(name, CloudWatchMeterRegistry(cloudwatchConfig, Clock.SYSTEM, cloudwatchClient))
        }

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

        val healthRouteHandler = WebHandler { context ->
            val unhealthyComponents = componentWithStatus(setOf(LifecycleStatus.ERROR))
            val status = if (unhealthyComponents.isEmpty()) {
                clearLastLogMessageForRoute(HTTP_HEALTH_ROUTE)
                ResponseCode.OK
            } else {
                logIfDifferentFromLastMessage(
                    HTTP_HEALTH_ROUTE,
                    "Status is unhealthy. The status of $unhealthyComponents has error."
                )
                ResponseCode.SERVICE_UNAVAILABLE
            }
            context.status(status)
            context.header(Header.CACHE_CONTROL, NO_CACHE)
            context
        }

        val statusRouteHandler = WebHandler { context ->
            val notReadyComponents = componentWithStatus(setOf(LifecycleStatus.DOWN, LifecycleStatus.ERROR))
            val status = if (notReadyComponents.isEmpty()) {
                clearLastLogMessageForRoute(HTTP_STATUS_ROUTE)
                ResponseCode.OK
            } else {
                logIfDifferentFromLastMessage(
                    HTTP_STATUS_ROUTE,
                    "There are components with error or down state: $notReadyComponents."
                )
                ResponseCode.SERVICE_UNAVAILABLE
            }
            context.status(status)
            context.result(objectMapper.writeValueAsString(lifecycleRegistry.componentStatus()))
            context.header(Header.CACHE_CONTROL, NO_CACHE)
            context
        }

        val metricsRouteHandler = WebHandler { context ->
            context.result(prometheusRegistry.scrape())
            context.header(Header.CACHE_CONTROL, NO_CACHE)
            context
        }

        webServer.registerEndpoint(Endpoint(HTTPMethod.GET, HTTP_HEALTH_ROUTE, healthRouteHandler))
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
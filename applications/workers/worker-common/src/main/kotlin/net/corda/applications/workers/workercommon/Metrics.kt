package net.corda.applications.workers.workercommon

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
import net.corda.metrics.CordaMetrics
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebHandler
import net.corda.web.api.WebServer
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient

object Metrics {
    private const val CORDA_NAMESPACE = "CORDA"
    private const val K8S_NAMESPACE_KEY = "K8S_NAMESPACE"
    private const val CLOUDWATCH_ENABLED_KEY = "ENABLE_CLOUDWATCH"
    private val logger = LoggerFactory.getLogger(Metrics::class.java)
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
    fun configure(webServer: WebServer, name: String) {
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

        val metricsRouteHandler = WebHandler { context ->
            context.result(prometheusRegistry.scrape())
            context.header(Header.CACHE_CONTROL, NO_CACHE)
            context
        }
        webServer.registerEndpoint(Endpoint(HTTPMethod.GET, HTTP_METRICS_ROUTE, metricsRouteHandler))
    }
}
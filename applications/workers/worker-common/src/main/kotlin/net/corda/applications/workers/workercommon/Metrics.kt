package net.corda.applications.workers.workercommon

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
import net.corda.metrics.CordaMetrics
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebHandler
import net.corda.web.api.WebServer
import org.slf4j.LoggerFactory

object Metrics {
    private val logger = LoggerFactory.getLogger(Metrics::class.java)
    private val prometheusRegistry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
//    private val fmr = MyLoggingMeterRegistry(logger::info, prometheusRegistry::scrape)
    fun configure(webServer: WebServer, name: String) {
        logger.info("Creating Prometheus metric registry")
        CordaMetrics.configure(name, prometheusRegistry)
//        CordaMetrics.configure(name, LoggingMeterRegistry())
//        CordaMetrics.configure(name, fmr)

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
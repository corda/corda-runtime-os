package net.corda.tracing

import brave.servlet.TracingFilter
import io.javalin.core.JavalinConfig
import net.corda.tracing.impl.TracingState
import org.eclipse.jetty.servlet.FilterHolder
import java.util.EnumSet
import javax.servlet.DispatcherType

/**
 * Configure service name that will be displayed in dashboards.
 * Defaults to "unknown".
 */
fun setTracingServiceName(serviceName: String) {
    TracingState.serviceName = serviceName
}

/**
 * Configure Zipkin host that will receive trace data in zipkin format. The value should include a port number if the
 * server is listening on the default 9411 port.
 *
 * Example value: http://localhost:9411
 *
 * Defaults to "" which will turn off sending data to a Zipkin host.
 */
fun setZipkinHost(zipkinHost: String) {
    TracingState.zipkinHost = zipkinHost
}

/**
 * Close tracing system, flushing buffers before shutdown.
 *
 * Call this method to avoid losing events at shutdown.
 */
fun shutdownTracing() {
    TracingState.close()
}

/**
 * Configure Javalin to read trace IDs from requests or generate new ones if missing.
 */
fun configureJavalinForTracing(config: JavalinConfig) {
    config.configureServletContextHandler { sch ->
        sch.addFilter(
            FilterHolder(TracingFilter.create(TracingState.tracing)),
            "/*",
            EnumSet.of(DispatcherType.INCLUDE, DispatcherType.REQUEST)
        )
    }
}

package net.corda.tracing

import brave.servlet.TracingFilter
import io.javalin.core.JavalinConfig
import net.corda.tracing.impl.TracingState
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.eclipse.jetty.servlet.FilterHolder
import java.util.EnumSet
import java.util.concurrent.ExecutorService
import javax.servlet.DispatcherType

/**
 * Configure service name that will be displayed in dashboards.
 * Defaults to "unknown".
 */
fun setTracingServiceName(serviceName: String) {
    TracingState.serviceName = serviceName
}

fun wrapWithTracingExecutor(executor: ExecutorService): ExecutorService {
    return TracingState.tracing.currentTraceContext().executorService(executor)
}

fun <K, V> wrapWithTracingConsumer(kafkaConsumer: Consumer<K, V>): Consumer<K, V> {
    return TracingState.kafkaTracing.consumer(kafkaConsumer)
}

fun <K, V> wrapWithTracingProducer(kafkaProducer: Producer<K, V>): Producer<K, V> {
    return TracingState.kafkaTracing.producer(kafkaProducer)
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



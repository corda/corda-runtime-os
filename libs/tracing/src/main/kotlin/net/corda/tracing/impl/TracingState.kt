package net.corda.tracing.impl

import brave.Tracing
import brave.context.slf4j.MDCScopeDecorator
import brave.kafka.clients.KafkaTracing
import brave.propagation.ThreadLocalCurrentTraceContext
import brave.sampler.Sampler
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.brave.ZipkinSpanHandler
import zipkin2.reporter.urlconnection.URLConnectionSender

/**
 * Tracing objects that will exist for the lifetime of the application.
 *
 * Close before shutdown to wait for trace spans to be sent to external systems.
 */
object TracingState : AutoCloseable {
    private val spanAsyncReporter =
        AsyncReporter.create(URLConnectionSender.create("http://localhost:9411/api/v2/spans"))
    private val spanHandler = ZipkinSpanHandler
        .create(spanAsyncReporter)

    private val braveCurrentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
        .addScopeDecorator(MDCScopeDecorator.get())
        .build()

    var serviceName = "unknown"

    val tracing: Tracing by lazy {
        Tracing.newBuilder()
            .currentTraceContext(braveCurrentTraceContext)
            .supportsJoin(false)
            .localServiceName(serviceName)
            .traceId128Bit(true)
            .sampler(Sampler.ALWAYS_SAMPLE)
            .addSpanHandler(spanHandler)
            .build()
    }

    val kafkaTracing: KafkaTracing by lazy {
        KafkaTracing.newBuilder(tracing)
            .singleRootSpanOnReceiveBatch(false)
            .build()
    }

    override fun close() {
        tracing.close()
        spanAsyncReporter.close()
    }
}

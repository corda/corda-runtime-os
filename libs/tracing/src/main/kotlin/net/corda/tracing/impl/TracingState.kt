package net.corda.tracing.impl

import brave.Tracing
import brave.context.slf4j.MDCScopeDecorator
import brave.propagation.ThreadLocalCurrentTraceContext
import brave.sampler.Sampler
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.brave.ZipkinSpanHandler
import zipkin2.reporter.urlconnection.URLConnectionSender
import java.net.URL
import java.util.Stack

/**
 * Tracing objects that will exist for the lifetime of the application.
 *
 * Close before shutdown to wait for trace spans to be sent to external systems.
 */
object TracingState: AutoCloseable {

    private val resourcesToClose = Stack<AutoCloseable>()

    var serviceName = "unknown"

    val tracing: Tracing by lazy {

        val braveCurrentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
            .addScopeDecorator(MDCScopeDecorator.get())
            .build()

        val tracingBuilder = Tracing.newBuilder()
            .currentTraceContext(braveCurrentTraceContext)
            .supportsJoin(false)
            .localServiceName(serviceName)
            .traceId128Bit(true)
            .sampler(Sampler.ALWAYS_SAMPLE)

        val zipkinHost = System.getenv("CORDA_TRACING_SERVER_ZIPKIN_PROTOCOL")
        if (zipkinHost != null) {
            val zipkinUrl = "$zipkinHost/api/v2/spans"
            val spanAsyncReporter =
                AsyncReporter.create(URLConnectionSender.create(zipkinUrl))
                    .also(resourcesToClose::push)
            val spanHandler = ZipkinSpanHandler.create(spanAsyncReporter)

            tracingBuilder.addSpanHandler(spanHandler)
        }

        tracingBuilder.build().also(resourcesToClose::push)
    }

    override fun close() {
        while (resourcesToClose.any()) {
            resourcesToClose.pop().close()
        }
    }
}

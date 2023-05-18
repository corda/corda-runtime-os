package net.corda.tracing

import brave.Tracing
import brave.context.slf4j.MDCScopeDecorator
import brave.propagation.ThreadLocalCurrentTraceContext
import brave.sampler.Sampler
import brave.servlet.TracingFilter
import io.javalin.core.JavalinConfig
import org.eclipse.jetty.servlet.FilterHolder
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.brave.ZipkinSpanHandler
import zipkin2.reporter.urlconnection.URLConnectionSender
import java.util.EnumSet
import javax.servlet.DispatcherType

private val spanAsyncReporter = AsyncReporter.create(URLConnectionSender.create("http://localhost:9411/api/v2/spans"))
private val spanHandler = ZipkinSpanHandler
    .create(spanAsyncReporter)

private val braveCurrentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
    .addScopeDecorator(MDCScopeDecorator.get())
    .build()

private var serviceName = "unknown"
fun setTracingServiceName(name: String) {
    serviceName = name
}

private val tracing: Tracing by lazy {
    Tracing.newBuilder()
        .currentTraceContext(braveCurrentTraceContext)
        .supportsJoin(false)
        .localServiceName(serviceName)
        .traceId128Bit(true)
        .sampler(Sampler.ALWAYS_SAMPLE)
        .addSpanHandler(spanHandler)
        .build()
}

fun configureJavalinForTracing(config: JavalinConfig) {
    config.configureServletContextHandler { sch ->
        sch.addFilter(
            FilterHolder(TracingFilter.create(tracing)),
            "/*",
            EnumSet.of(DispatcherType.INCLUDE, DispatcherType.REQUEST)
        )
    }
}
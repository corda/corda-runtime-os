package net.corda.tracing.impl

import brave.Tracing
import brave.baggage.BaggageField
import brave.baggage.BaggagePropagation
import brave.baggage.BaggagePropagationConfig
import brave.context.slf4j.MDCScopeDecorator
import brave.kafka.clients.KafkaTracing
import brave.propagation.B3Propagation
import brave.propagation.ThreadLocalCurrentTraceContext
import brave.sampler.Sampler
import zipkin2.Span
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.Reporter
import zipkin2.reporter.brave.ZipkinSpanHandler
import zipkin2.reporter.urlconnection.URLConnectionSender
import java.util.Stack

object TracingState : AutoCloseable {

    private val resourcesToClose = Stack<AutoCloseable>()
    val requestId: BaggageField = BaggageField.create("request_id")
    val virtualNodeId: BaggageField = BaggageField.create("vnode_id")
    val transactionId: BaggageField = BaggageField.create("tx_id")

    var serviceName = "unknown"
    var zipkinHost = ""

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
            .propagationFactory(
                BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
                    .add(BaggagePropagationConfig.SingleBaggageField.remote(requestId))
                    .add(BaggagePropagationConfig.SingleBaggageField.remote(virtualNodeId))
                    .add(BaggagePropagationConfig.SingleBaggageField.remote(transactionId))
                    .build()
            )

        val reporters = mutableListOf<Reporter<Span>>(Reporter.CONSOLE)
        val reporter = CombinedSpanReporter(reporters)

        if (zipkinHost.isNotEmpty()) {
            val zipkinUrl = "$zipkinHost/api/v2/spans"
            val spanAsyncReporter =
                AsyncReporter.create(URLConnectionSender.create(zipkinUrl))
                    .also(resourcesToClose::push)
            reporters.add(spanAsyncReporter)

            val spanHandler = ZipkinSpanHandler.create(reporter)

            tracingBuilder.addSpanHandler(spanHandler)
        }

        tracingBuilder.build().also(resourcesToClose::push)
    }

    val kafkaTracing: KafkaTracing by lazy {
        KafkaTracing.newBuilder(tracing)
            .singleRootSpanOnReceiveBatch(false)
            .build()
    }

    val recordTracing: RecordTracing by lazy { RecordTracing(tracing) }

    override fun close() {
        while (resourcesToClose.any()) {
            resourcesToClose.pop().close()
        }
    }
}

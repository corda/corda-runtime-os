package net.corda.tracing.impl

import brave.Tracing
import brave.baggage.BaggagePropagation
import brave.baggage.BaggagePropagationConfig
import brave.context.slf4j.MDCScopeDecorator
import brave.kafka.clients.KafkaTracing
import brave.propagation.B3Propagation
import brave.propagation.ThreadLocalCurrentTraceContext
import brave.sampler.RateLimitingSampler
import brave.sampler.Sampler
import brave.servlet.TracingFilter
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.tracing.BatchRecordTracer
import net.corda.tracing.TraceContext
import net.corda.tracing.TracingService
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import zipkin2.Span
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.Reporter
import zipkin2.reporter.brave.ZipkinSpanHandler
import zipkin2.reporter.urlconnection.URLConnectionSender
import java.util.OptionalInt
import java.util.Stack
import java.util.concurrent.ExecutorService
import javax.servlet.Filter

@Suppress("TooManyFunctions")
class BraveTracingService(serviceName: String, zipkinHost: String, samplesPerSecond: OptionalInt) : TracingService {

    private val resourcesToClose = Stack<AutoCloseable>()

    private val tracing: Tracing by lazy {

        val braveCurrentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
            .addScopeDecorator(MDCScopeDecorator.get())
            .build()

        val sampler = if (samplesPerSecond.isPresent)
            RateLimitingSampler.create(samplesPerSecond.asInt)
        else
            Sampler.ALWAYS_SAMPLE

        val tracingBuilder = Tracing.newBuilder()
            .currentTraceContext(braveCurrentTraceContext)
            .supportsJoin(false)
            .localServiceName(serviceName)
            .traceId128Bit(true)
            .sampler(sampler)
            .propagationFactory(
                BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
                    .add(BaggagePropagationConfig.SingleBaggageField.remote(BraveBaggageFields.REQUEST_ID))
                    .add(BaggagePropagationConfig.SingleBaggageField.remote(BraveBaggageFields.VIRTUAL_NODE_ID))
                    .build()
            )

        // The console reporter is useful when debugging test runs on the combined worker.
        // uncomment it to enable it.
        val reporters = mutableListOf<Reporter<Span>>(/*Reporter.CONSOLE*/)
        val reporter = CombinedSpanReporter(reporters)

        val zipkinUrl = "$zipkinHost/api/v2/spans"
        val spanAsyncReporter =
            AsyncReporter.create(URLConnectionSender.create(zipkinUrl))
                .also(resourcesToClose::push)
        reporters.add(spanAsyncReporter)

        val spanHandler = ZipkinSpanHandler.create(reporter)

        tracingBuilder.addSpanHandler(spanHandler)
        tracingBuilder.build().also(resourcesToClose::push)
    }

    private val recordInjector by lazy {
        tracing.propagation()
            .injector { param: MutableList<Pair<String, String>>, key: String, value: String ->
                param.removeAll { it.first == key }
                param.add(key to value)
            }
    }

    private val kafkaTracing: KafkaTracing by lazy {
        KafkaTracing.newBuilder(tracing)
            .singleRootSpanOnReceiveBatch(false)
            .build()
    }

    private val recordTracing: BraveRecordTracing by lazy { BraveRecordTracing(tracing) }

    override fun addTraceHeaders(headers: List<Pair<String, String>>): List<Pair<String, String>> {
        val headersWithTracing = headers.toMutableList()
        recordInjector.inject(tracing.currentTraceContext().get(), headersWithTracing)
        return headersWithTracing
    }

    override fun traceBatch(operationName: String): BatchRecordTracer {
        return BraveBatchRecordTracer(operationName, recordTracing, recordInjector)
    }

    override fun <R> nextSpan(operationName: String, processingBlock: TraceContext.() -> R): R {
        return tracing.tracer().nextSpan().doTrace(operationName) {
            val ctx = BraveTraceContext(this)
            processingBlock(ctx)
        }
    }

    override fun <R> nextSpan(operationName: String, record: Record<*, *>, processingBlock: TraceContext.() -> R): R {
        return recordTracing.nextSpan(record).doTrace(operationName) {
            val ctx = BraveTraceContext(this)
            processingBlock(ctx)
        }
    }

    override fun <R> nextSpan(
        operationName: String,
        record: EventLogRecord<*, *>,
        processingBlock: TraceContext.() -> R
    ): R {
        return recordTracing.nextSpan(record).doTrace(operationName) {
            val ctx = BraveTraceContext(this)
            processingBlock(ctx)
        }
    }

    override fun wrapWithTracingExecutor(executor: ExecutorService): ExecutorService {
        return tracing.currentTraceContext().executorService(executor)
    }

    override fun <K, V> wrapWithTracingConsumer(kafkaConsumer: Consumer<K, V>): Consumer<K, V> {
        return kafkaTracing.consumer(kafkaConsumer)
    }

    override fun <K, V> wrapWithTracingProducer(kafkaProducer: Producer<K, V>): Producer<K, V> {
        return kafkaTracing.producer(kafkaProducer)
    }

    override fun getTracedServletFilter(): Filter {
        return TracingFilter.create(tracing)
    }

    override fun close() {
        while (resourcesToClose.any()) {
            resourcesToClose.pop().close()
        }
    }

    private fun <T> brave.Span.doTrace(operationName: String, blockOnSpan: brave.Span.() -> T): T {
        name(operationName).start()
        return tracing.currentTraceContext().newScope(context()).use {
            try {
                blockOnSpan()
            } catch (ex: Exception) {
                error(ex)
                throw ex
            } finally {
                finish()
            }
        }
    }
}
package net.corda.tracing.brave

import brave.Tracer
import brave.Tracing
import brave.baggage.BaggagePropagation
import brave.baggage.BaggagePropagationConfig
import brave.baggage.CorrelationScopeConfig
import brave.context.slf4j.MDCScopeDecorator
import brave.http.HttpRequest
import brave.http.HttpRequestMatchers.methodEquals
import brave.http.HttpRequestMatchers.pathStartsWith
import brave.http.HttpRequestParser
import brave.http.HttpRuleSampler
import brave.http.HttpTracing
import brave.propagation.B3Propagation
import brave.propagation.ThreadLocalCurrentTraceContext
import brave.sampler.Matchers.and
import brave.sampler.RateLimitingSampler
import brave.sampler.Sampler
import brave.sampler.SamplerFunction
import brave.servlet.TracingFilter
import io.javalin.core.JavalinConfig
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.tracing.BatchPublishTracing
import net.corda.tracing.BatchRecordTracer
import net.corda.tracing.TraceContext
import net.corda.tracing.TracingService
import net.corda.utilities.debug
import org.eclipse.jetty.servlet.FilterHolder
import org.slf4j.LoggerFactory
import zipkin2.Span
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.BytesMessageSender
import zipkin2.reporter.Reporter
import zipkin2.reporter.brave.ZipkinSpanHandler
import zipkin2.reporter.urlconnection.URLConnectionSender
import java.util.EnumSet
import java.util.Stack
import java.util.concurrent.ExecutorService
import java.util.logging.Level
import java.util.logging.Logger
import javax.servlet.DispatcherType

internal sealed interface SampleRate
internal object Unlimited : SampleRate
internal data class PerSecond(val samplesPerSecond: Int) : SampleRate

@Suppress("TooManyFunctions")
internal class BraveTracingService(serviceName: String, zipkinHost: String?, samplesPerSecond: SampleRate) :
    TracingService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val currentBatchPublishingTracers =
        ThreadLocal.withInitial { mutableMapOf<String, BraveBatchPublishTracing>() }

    private val resourcesToClose = Stack<AutoCloseable>()

    private val tracing: Tracing by lazy {

        val braveCurrentTraceContext = ThreadLocalCurrentTraceContext.newBuilder().addScopeDecorator(
            MDCScopeDecorator.newBuilder()
                .add(CorrelationScopeConfig.SingleCorrelationField.create(BraveBaggageFields.REQUEST_ID)).build()
        ).build()

        val sampler = sampler(samplesPerSecond)
        when (samplesPerSecond) {
            is PerSecond -> {
                logger.info("Tracing will sample ${samplesPerSecond.samplesPerSecond} requests per second")
            }

            is Unlimited -> {
                logger.info("Tracing will sample unlimited requests per second")
            }
        }

        val tracingBuilder = Tracing.newBuilder().currentTraceContext(braveCurrentTraceContext).supportsJoin(false)
            .localServiceName(serviceName).traceId128Bit(true).sampler(sampler).propagationFactory(
                BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
                    .add(BaggagePropagationConfig.SingleBaggageField.remote(BraveBaggageFields.REQUEST_ID))
                    .add(BaggagePropagationConfig.SingleBaggageField.remote(BraveBaggageFields.VIRTUAL_NODE_ID)).build()
            )

        val reporters = mutableListOf<Reporter<Span>>()

        //Establish zipkin connection iff url host is provided and create respective reporter
        if (zipkinHost != null) {
            val zipkinUrl = "$zipkinHost/api/v2/spans"
            val sender: BytesMessageSender = URLConnectionSender.create(zipkinUrl)
            val spanAsyncReporter = AsyncReporter.create(sender).also(resourcesToClose::push)
            reporters.add(spanAsyncReporter)
        }

        // LogReporter will report trace spans to local log files
        reporters.add(LogReporter())
        val reporter = CombinedSpanReporter(reporters)
        val spanHandler = ZipkinSpanHandler.create(reporter)
        tracingBuilder.addSpanHandler(spanHandler)
        tracingBuilder.build().also(resourcesToClose::push)
    }

    private fun sampler(samplesPerSecond: SampleRate): Sampler = when (samplesPerSecond) {
        is PerSecond -> {
            RateLimitingSampler.create(samplesPerSecond.samplesPerSecond)
        }

        is Unlimited -> {
            Sampler.ALWAYS_SAMPLE
        }
    }

    private val serverSampler: SamplerFunction<HttpRequest> = HttpRuleSampler.newBuilder()
        .putRule(and(methodEquals("POST"), pathStartsWith("/api/v5_1/flow")), sampler(samplesPerSecond))
        .putRule(and(methodEquals("POST"), pathStartsWith("/api/v1/flow")), sampler(samplesPerSecond))
        .putRule(pathStartsWith("/"), sampler(samplesPerSecond)).build()

    private val httpTracing by lazy {
        HttpTracing.newBuilder(tracing)
            .serverRequestParser(
                object : HttpRequestParser.Default() {
                    override fun spanName(req: HttpRequest?, context: brave.propagation.TraceContext?): String {
                        return "http server - ${req?.method()} - ${req?.path()}"
                    }
                }
            )
        .serverSampler(serverSampler).build()
    }

    private class LogReporter : Reporter<Span> {
        private val logger: Logger = Logger.getLogger(LogReporter::class.java.name)

        override fun report(span: Span) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info(span.toString())
            }
        }

        override fun toString(): String {
            return "LogReporter{name=${logger.name}}"
        }
    }

    private val tracer: Tracer by lazy {
        tracing.tracer()
    }

    private val recordInjector by lazy {
        BraveRecordInjector(tracing)
    }

    private val recordTracing: BraveRecordTracing by lazy { BraveRecordTracing(tracing) }

    override fun addTraceHeaders(
        headers: List<Pair<String, String>>,
        traceHeadersToOverrideContext: List<Pair<String, String>>
    ): List<Pair<String, String>> {
        val ctx = recordTracing.getTraceContext(traceHeadersToOverrideContext)
        return if (ctx == null) {
            logger.debug { "Tracing context is not set" }
            headers
        } else {
            recordInjector.inject(ctx, headers)
        }
    }

    override fun addTraceHeaders(
        headers: List<Pair<String, String>>,
        traceHeadersToOverrideContext: Map<String, Any>
    ): List<Pair<String, String>> {
        val ctx = recordTracing.getTraceContext(traceHeadersToOverrideContext)
        return if (ctx == null) {
            logger.debug { "Tracing context is not set" }
            headers
        } else {
            recordInjector.inject(ctx, headers)
        }
    }

    override fun addTraceHeaders(
        headers: Map<String, Any>,
        traceHeadersToOverrideContext: Map<String, Any>
    ): Map<String, Any> {
        val ctx = recordTracing.getTraceContext(traceHeadersToOverrideContext)
        return if (ctx == null) {
            logger.debug { "Tracing context is not set" }
            headers
        } else {
            return recordInjector.inject(ctx, headers)
        }
    }

    override fun traceBatch(operationName: String): BatchRecordTracer {
        return BraveBatchRecordTracer(operationName, recordTracing, recordInjector)
    }

    override fun <R> nextSpan(operationName: String, processingBlock: TraceContext.() -> R): R {
        return tracer.nextSpan().doTrace(operationName) {
            val ctx = BraveTraceContext(tracer, this)
            processingBlock(ctx)
        }
    }

    override fun <R> nextSpan(operationName: String, record: Record<*, *>, processingBlock: TraceContext.() -> R): R {
        return recordTracing.nextSpan(record).doTrace(operationName) {
            val ctx = BraveTraceContext(tracer, this)
            processingBlock(ctx)
        }
    }

    override fun <R> nextSpan(
        operationName: String, record: EventLogRecord<*, *>, processingBlock: TraceContext.() -> R
    ): R {
        return recordTracing.nextSpan(record).doTrace(operationName) {
            val ctx = BraveTraceContext(tracer, this)
            processingBlock(ctx)
        }
    }

    override fun <R> joinSpan(operationName: String, record: Record<*, *>, processingBlock: TraceContext.() -> R): R {
        return recordTracing.joinSpan(record.headers).doTrace(operationName) {
            val ctx = BraveTraceContext(tracer, this)
            processingBlock(ctx)
        }
    }

    override fun nextSpan(
        operationName: String, headers: List<Pair<String, String>>
    ): TraceContext {
        val span = recordTracing.nextSpan(headers).name(operationName).start()
        return BraveTraceContext(tracer, span)
    }

    override fun nextSpan(
        operationName: String, headers:  Map<String, Any>
    ): TraceContext {
        val span = recordTracing.nextSpan(headers).name(operationName).start()
        return BraveTraceContext(tracer, span)
    }

    override fun <R> nextSpan(
        operationName: String, headers:  Map<String, Any>, processingBlock: () -> R
    ): R {
        return recordTracing.nextSpan(headers).doTrace(operationName) {
            BraveTraceContext(tracer, this)
            processingBlock()
        }
    }

    override fun getOrCreateBatchPublishTracing(clientId: String): BatchPublishTracing {
        return currentBatchPublishingTracers.get()
            .getOrPut(clientId) { recordTracing.createBatchPublishTracing(clientId) }
    }

    override fun wrapWithTracingExecutor(executor: ExecutorService): ExecutorService {
        return tracing.currentTraceContext().executorService(executor)
    }

    override fun configureJavalin(config: Any) {
        (config as JavalinConfig).configureServletContextHandler { sch ->
            sch.addFilter(
                FilterHolder(TracingFilter.create(httpTracing)),
                "/*",
                EnumSet.of(DispatcherType.INCLUDE, DispatcherType.REQUEST)
            )
        }
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
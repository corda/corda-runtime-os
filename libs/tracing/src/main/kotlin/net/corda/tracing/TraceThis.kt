@file:Suppress("TooManyFunctions")
package net.corda.tracing

import brave.Span
import brave.servlet.TracingFilter
import io.javalin.core.JavalinConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.tracing.impl.BatchRecordTracerImpl
import net.corda.tracing.impl.TraceContextImpl
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

fun traceBatch(operationName: String): BatchRecordTracer {
    return BatchRecordTracerImpl(operationName)
}

private fun <T> Span.doTrace(operationName: String, blockOnSpan: Span.() -> T): T {
    name(operationName).start()
    return TracingState.tracing.currentTraceContext().newScope(context()).use {
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

fun <R> trace(operationName: String, processingBlock: TraceContext.() -> R): R {
    return TracingState.tracing.tracer().nextSpan().doTrace(operationName) {
        val ctx = TraceContextImpl(this)
        processingBlock(ctx)
    }
}

private val recordInjector by lazy {
    TracingState.tracing.propagation()
        .injector { param: MutableList<Pair<String, String>>, key: String, value: String ->
            param.removeAll { it.first == key }
            param.add(key to value)
        }
}

fun addTraceContextToRecords(records: List<Record<*, *>>): List<Record<*, *>> = records.map(::addTraceContextToRecord)

fun addTraceContextToRecord(it: Record<*, *>): Record<out Any, out Any> {
    val headersWithTracing = it.headers.toMutableList()
    recordInjector.inject(TracingState.tracing.currentTraceContext().get(), headersWithTracing)
    return it.copy(headers = headersWithTracing)
}

fun List<Record<*, *>>.excludeTracingHeaders() = this.map { record ->
    record.copy(headers = record.headers.filterNot { (key, _) ->
        key.startsWith("X-B3-")
    })
}
fun <S : Any> StateAndEventProcessor.Response<S>.excludeTracingHeaders() =
    copy(responseEvents = responseEvents.excludeTracingHeaders())

fun addTraceContextToRecords(records: List<Record<*, *>>, span: Span): List<Record<*, *>> =
    records.map { addTraceContextToRecord(it, span) }

fun addTraceContextToRecord(it: Record<*, *>, span: Span): Record<out Any, out Any> {
    val headersWithTracing = it.headers.toMutableList()
    recordInjector.inject(span.context(), headersWithTracing)
    return it.copy(headers = headersWithTracing)
}

fun traceEventProcessing(
    event: Record<*, *>,
    operationName: String,
    processingBlock: () -> List<Record<*, *>>
): List<Record<*, *>> {
    return TracingState.recordTracing.nextSpan(event).doTrace(operationName) {
        addTraceContextToRecords(processingBlock())
    }
}

fun traceEventProcessing(
    event: EventLogRecord<*, *>,
    operationName: String,
    processingBlock: () -> List<Record<*, *>>
): List<Record<*, *>> {
    return TracingState.recordTracing.nextSpan(event).doTrace(operationName) {
        addTraceContextToRecords(processingBlock())
    }
}

fun traceEventProcessingNullableSingle(
    event: Record<*, *>,
    operationName: String,
    processingBlock: () -> Record<*, *>?
): Record<*, *>? {
    return TracingState.recordTracing.nextSpan(event).doTrace(operationName) {
        processingBlock()?.let { addTraceContextToRecord(it) }
    }
}

fun traceEventProcessingSingle(
    event: Record<*, *>,
    operationName: String,
    processingBlock: () -> Record<*, *>
): Record<*, *> {
    return TracingState.recordTracing.nextSpan(event).doTrace(operationName) {
        addTraceContextToRecord(processingBlock())
    }
}

fun <K : Any, S : Any, V : Any> traceStateAndEventExecution(
    event: Record<K, V>,
    operationName: String,
    processingBlock: () -> StateAndEventProcessor.Response<S>
): StateAndEventProcessor.Response<S> {
    return TracingState.recordTracing.nextSpan(event).doTrace(operationName) {
        val result = processingBlock()
        result.copy(responseEvents = addTraceContextToRecords(result.responseEvents))
    }
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

package net.corda.tracing

import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import java.util.concurrent.ExecutorService

interface TracingService : AutoCloseable {

    // The method takes the current headers and joins them with the trace headers
    // A trace context provides the trace headers. Normally, the activate context is used, but it can be overridden
    // by setting the parameter `traceHeadersToOverrideContext`.
    // The method returns a list that contains the current headers plus the trace headers.
    fun addTraceHeaders(
        headers: List<Pair<String, String>>,
        traceHeadersToOverrideContext: List<Pair<String, String>>
    ): List<Pair<String, String>>

    fun addTraceHeaders(
        headers: List<Pair<String, String>>,
        traceHeadersToOverrideContext: MutableMap<String, Any>
    ): List<Pair<String, String>>

    fun addTraceHeaders(
        headers: MutableMap<String, Any>,
        traceHeadersToOverrideContext: MutableMap<String, Any>
    ): MutableMap<String, Any>

    fun <R> nextSpan(operationName: String, processingBlock: TraceContext.() -> R): R

    fun <R> nextSpan(operationName: String, record: Record<*, *>, processingBlock: TraceContext.() -> R): R

    fun <R> nextSpan(operationName: String, record: EventLogRecord<*, *>, processingBlock: TraceContext.() -> R): R

    fun nextSpan(
        operationName: String,
        headers: List<Pair<String, String>>): TraceContext

    fun nextSpan(
        operationName: String,
        headers:  MutableMap<String, Any>): TraceContext

    fun getOrCreateBatchPublishTracing(clientId: String): BatchPublishTracing

    fun wrapWithTracingExecutor(executor: ExecutorService): ExecutorService

    fun configureJavalin(config: Any)

    fun traceBatch(operationName: String): BatchRecordTracer
}

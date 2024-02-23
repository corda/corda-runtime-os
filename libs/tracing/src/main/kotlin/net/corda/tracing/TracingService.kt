package net.corda.tracing

import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import java.util.concurrent.ExecutorService

interface TracingService : AutoCloseable {

    /**
     * The method takes the current headers and joins them with the trace headers.
     * Normally, the active context is used for setting the trace headers, but it can be overridden
     * by setting the parameter `traceHeadersToOverrideContext`.
     *
     * @return a list that contains the current headers plus the trace headers.
     */
    fun addTraceHeaders(
        headers: List<Pair<String, String>>,
        traceHeadersToOverrideContext: List<Pair<String, String>>
    ): List<Pair<String, String>>

    /**
     * The method takes the current headers and joins them with the trace headers.
     * Normally, the active context is used for setting the trace headers, but it can be overridden
     * by setting the parameter `traceHeadersToOverrideContext`.
     *
     * @return a list that contains the current headers plus the trace headers.
     */
    fun addTraceHeaders(
        headers: List<Pair<String, String>>,
        traceHeadersToOverrideContext: Map<String, Any>
    ): List<Pair<String, String>>

    /**
     * The method takes the current headers and joins them with the trace headers.
     * Normally, the active context is used for setting the trace headers, but it can be overridden
     * by setting the parameter `traceHeadersToOverrideContext`.
     *
     * @return a map that contains the current headers plus the trace headers.
     */
    fun addTraceHeaders(
        headers: Map<String, Any>,
        traceHeadersToOverrideContext: Map<String, Any>
    ): Map<String, Any>

    fun <R> nextSpan(operationName: String, processingBlock: TraceContext.() -> R): R

    fun <R> nextSpan(operationName: String, record: Record<*, *>, processingBlock: TraceContext.() -> R): R

    fun <R> nextSpan(operationName: String, record: EventLogRecord<*, *>, processingBlock: TraceContext.() -> R): R

    fun nextSpan(
        operationName: String,
        headers: List<Pair<String, String>>): TraceContext

    fun <R> nextSpan(
        operationName: String,
        headers: Map<String, Any>,
        processingBlock: () -> R): R

    fun nextSpan(
        operationName: String,
        headers: Map<String, Any>): TraceContext

    fun <R> joinSpan(operationName: String, record: Record<*, *>, processingBlock: TraceContext.() -> R): R

    fun getOrCreateBatchPublishTracing(clientId: String): BatchPublishTracing

    fun wrapWithTracingExecutor(executor: ExecutorService): ExecutorService

    fun configureJavalin(config: Any)

    fun traceBatch(operationName: String): BatchRecordTracer
}

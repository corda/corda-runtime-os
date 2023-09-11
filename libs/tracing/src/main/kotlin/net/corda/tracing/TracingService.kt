package net.corda.tracing

import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import java.util.concurrent.ExecutorService

interface TracingService : AutoCloseable {

    fun addTraceHeaders(headers: List<Pair<String, String>>): List<Pair<String, String>>

    fun <R> nextSpan(operationName: String, processingBlock: TraceContext.() -> R): R

    fun <R> nextSpan(operationName: String, record: Record<*, *>, processingBlock: TraceContext.() -> R): R

    fun <R> nextSpan(operationName: String, record: EventLogRecord<*, *>, processingBlock: TraceContext.() -> R): R

    fun nextSpan(
        operationName: String,
        headers: List<Pair<String, String>>): TraceContext

    fun getOrCreateBatchPublishTracing(clientId: String): BatchPublishTracing

    fun wrapWithTracingExecutor(executor: ExecutorService): ExecutorService

    fun configureJavalin(config: Any)

    fun traceBatch(operationName: String): BatchRecordTracer
}

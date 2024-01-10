package net.corda.tracing.impl

import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.tracing.BatchPublishTracing
import net.corda.tracing.BatchRecordTracer
import net.corda.tracing.TraceContext
import net.corda.tracing.TracingService
import java.util.concurrent.ExecutorService

class NoopTracingService : TracingService {

    class NoopTraceContext : TraceContext {
        override fun traceTag(key: String, value: String) {
        }

        override fun traceRequestId(requestId: String) {
        }

        override fun traceVirtualNodeId(vNodeId: String) {
        }

        override fun markInScope(): AutoCloseable {
            return AutoCloseable {  }
        }

        override fun errorAndFinish(e: Exception) {
        }

        override fun error(e: Exception) {
        }

        override fun finish() {
        }
    }

    class NoopBatchRecordTracer : BatchRecordTracer {
        override fun startSpanFor(event: Record<*, *>, correlationId: String) {
        }

        override fun errorSpanFor(correlationId: String, error: Exception, outputRecord: Record<*, *>): Record<*, *> {
            return outputRecord
        }

        override fun completeSpanFor(correlationId: String, outputRecord: Record<*, *>): Record<*, *> {
            return outputRecord
        }
    }

    class NoopBatchPublishTracing : BatchPublishTracing {
        override fun begin(recordHeaders: List<List<Pair<String, String>>>) {
        }

        override fun complete() {
        }

        override fun abort() {
        }
    }

    override fun addTraceHeaders(headers: List<Pair<String, String>>): List<Pair<String, String>> {
        return headers
    }

    override fun <R> nextSpan(operationName: String, processingBlock: TraceContext.() -> R): R {
        return processingBlock(NoopTraceContext())
    }

    override fun <R> nextSpan(operationName: String, record: Record<*, *>, processingBlock: TraceContext.() -> R): R {
        return processingBlock(NoopTraceContext())
    }

    override fun <R> nextSpan(
        operationName: String,
        record: EventLogRecord<*, *>,
        processingBlock: TraceContext.() -> R
    ): R {
        return processingBlock(NoopTraceContext())
    }

    override fun nextSpan(
        operationName: String,
        headers: List<Pair<String, String>>
    ): TraceContext {
        return NoopTraceContext()
    }

    override fun getOrCreateBatchPublishTracing(clientId: String): BatchPublishTracing {
        return NoopBatchPublishTracing()
    }

    override fun wrapWithTracingExecutor(executor: ExecutorService): ExecutorService {
        return executor
    }

    override fun configureJavalin(config: Any) {
    }

    override fun traceBatch(operationName: String): BatchRecordTracer {
        return NoopBatchRecordTracer()
    }

    override fun close() {
    }
}

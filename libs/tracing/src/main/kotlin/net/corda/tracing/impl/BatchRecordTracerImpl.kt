package net.corda.tracing.impl

import brave.Span
import net.corda.messaging.api.records.Record
import net.corda.tracing.BatchRecordTracer
import net.corda.tracing.addTraceContextToRecord
import net.corda.tracing.addTraceContextToRecords

class BatchRecordTracerImpl(private val operationName: String) : BatchRecordTracer {
    private val spanMap = mutableMapOf<String, Span>()

    override fun startSpanFor(event: Record<*, *>, correlationId: String) {
        spanMap[correlationId] = TracingState.recordTracing.nextSpan(event).name(operationName).start()
    }

    override fun errorSpanFor(correlationId: String, error: Exception, outputRecords: List<Record<*, *>>) {
        spanMap[correlationId]?.let {
            addTraceContextToRecords(outputRecords, it)
            it.error(error)
        }
    }

    override fun errorSpanFor(correlationId: String, error: Exception, outputRecord: Record<*, *>) {
        spanMap[correlationId]?.let {
            addTraceContextToRecord(outputRecord, it)
            it.error(error)
        }
    }

    override fun completeSpanFor(correlationId: String, outputRecords: List<Record<*, *>>) {
        spanMap[correlationId]?.let {
            addTraceContextToRecords(outputRecords, it)
            it.finish()
        }
    }

    override fun completeSpanFor(correlationId: String, outputRecord: Record<*, *>) {
        spanMap[correlationId]?.let {
            addTraceContextToRecord(outputRecord, it)
            it.finish()
        }
    }
}
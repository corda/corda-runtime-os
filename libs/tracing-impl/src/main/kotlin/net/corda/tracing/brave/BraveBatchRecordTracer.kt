package net.corda.tracing.brave

import brave.Span
import net.corda.messaging.api.records.Record
import net.corda.tracing.BatchRecordTracer

class BraveBatchRecordTracer(
    private val operationName: String,
    private val recordTracing: BraveRecordTracing,
    private val recordInjector: BraveRecordInjector
) : BatchRecordTracer {

    private val spanMap = mutableMapOf<String, Span>()

    override fun startSpanFor(event: Record<*, *>, correlationId: String) {
        spanMap[correlationId] = recordTracing.nextSpan(event).name(operationName).start()
    }

    override fun errorSpanFor(correlationId: String, error: Exception, outputRecord: Record<*, *>): Record<*, *> {
        return spanMap[correlationId]?.let {
            val updatedRecord = addTraceContextToRecord(outputRecord, it)
            it.error(error)
            updatedRecord
        } ?: outputRecord
    }

    override fun completeSpanFor(correlationId: String, outputRecord: Record<*, *>) : Record<*, *> {
        return spanMap[correlationId]?.let {
            val updatedRecord = addTraceContextToRecord(outputRecord, it)
            it.finish()
            updatedRecord
        } ?: outputRecord
    }

    private fun addTraceContextToRecord(it: Record<*, *>, span: Span): Record<out Any, out Any> {
        val headersWithTracing = it.headers.toMutableList()
        recordInjector.inject(span.context(), headersWithTracing)
        return it.copy(headers = headersWithTracing)
    }
}
package net.corda.tracing.brave

import brave.Span
import brave.Tracing
import brave.propagation.TraceContextOrSamplingFlags
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record

class BraveRecordTracing(tracing: Tracing) {
    private val tracer = tracing.tracer()
    private val recordExtractor = BraveRecordExtractor(tracing)

    fun extract(headers: List<Pair<String, String>>): TraceContextOrSamplingFlags? {
        return recordExtractor.extract(headers)
    }
    fun extract(headers: MutableMap<String, Any>): TraceContextOrSamplingFlags? {
        return recordExtractor.extract(headers)
    }

    fun nextSpan(record: Record<*, *>): Span {
        return nextSpan(record.headers)
    }

    fun nextSpan(record: EventLogRecord<*, *>): Span {
        return nextSpan(record.headers)
    }

    fun nextSpan(headers: List<Pair<String, String>>): Span {
        val extracted = recordExtractor.extract(headers)
        return nextSpan(extracted)
    }

    fun nextSpan(headers: MutableMap<String, Any>): Span {
        val extracted = recordExtractor.extract(headers)
        return nextSpan(extracted)
    }

    private fun nextSpan(extracted: TraceContextOrSamplingFlags?): Span {
        if (extracted == null) {
            return tracer.nextSpan()
        }

        return tracer.nextSpan(extracted)
    }

    fun createBatchPublishTracing(clientId: String): BraveBatchPublishTracing {
        return BraveBatchPublishTracing(clientId, tracer, recordExtractor)
    }
}


package net.corda.tracing.brave

import brave.Span
import brave.Tracing
import brave.propagation.Propagation
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record

class BraveRecordTracing(tracing: Tracing) {
    private val tracer = tracing.tracer()
    private val recordHeaderGetter: Propagation.Getter<List<Pair<String, String>>, String> =
        Propagation.Getter<List<Pair<String, String>>, String> { request, key ->
            request.reversed().firstOrNull { it.first == key }?.second
        }
    private val tracingContextExtractor = tracing.propagation().extractor(recordHeaderGetter)

    fun nextSpan(record: Record<*, *>): Span {
        return nextSpan(record.headers)
    }

    fun nextSpan(record: EventLogRecord<*, *>): Span {
        return nextSpan(record.headers)
    }

    fun nextSpan(headers: List<Pair<String, String>>): Span {
        val extracted = tracingContextExtractor.extract(headers)
        return if (extracted == null) {
            tracer.nextSpan()
        } else {
            tracer.nextSpan(extracted)
        }
    }

    fun createBatchPublishTracing(clientId: String): BraveBatchPublishTracing {
        return BraveBatchPublishTracing(clientId, tracer, tracingContextExtractor)
    }
}


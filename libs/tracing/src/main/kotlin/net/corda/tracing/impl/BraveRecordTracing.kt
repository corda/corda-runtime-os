package net.corda.tracing.impl

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
        return nextSpan(record.topic, record.headers)
    }

    fun nextSpan(record: EventLogRecord<*, *>): Span {
        return nextSpan(record.topic, record.headers)
    }

    fun createBatchPublishTracing(clientId: String): BraveBatchPublishTracing {
        return BraveBatchPublishTracing(clientId, tracer, tracingContextExtractor)
    }

    private fun nextSpan(topic: String, headers: List<Pair<String, String>>): Span {
        val extracted = tracingContextExtractor.extract(headers)
        val span = tracer.nextSpan(extracted)
        if (extracted.context() == null && !span.isNoop) {
            span.tag("kafka.topic", topic)
        }

        return span
    }
}


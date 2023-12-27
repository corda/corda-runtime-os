package net.corda.tracing.brave

import brave.Span
import brave.Tracing
import brave.propagation.Propagation
import brave.propagation.TraceContextOrSamplingFlags
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record

class BraveRecordTracing(tracing: Tracing) {
    private val tracer = tracing.tracer()
    private val recordHeaderGetter: Propagation.Getter<List<Pair<String, String>>, String> =
        Propagation.Getter<List<Pair<String, String>>, String> { request, key ->
            request.reversed().firstOrNull { it.first == key }?.second
        }
    private val recordExtractor = tracing.propagation().extractor(recordHeaderGetter)

    private val messageMediatorHeaderGetter: Propagation.Getter<MutableMap<String, Any>, String> =
        Propagation.Getter<MutableMap<String, Any>, String> { message, key -> message[key] as String }
    private val messageMediatorExtractor = tracing.propagation().extractor(messageMediatorHeaderGetter)

    fun nextSpan(record: Record<*, *>): Span {
        return nextSpan(record.headers)
    }

    fun nextSpan(record: EventLogRecord<*, *>): Span {
        return nextSpan(record.headers)
    }

    fun nextSpan(headers: List<Pair<String, String>>): Span {
        val extracted = extractTraceContext(headers)
        return if (extracted == null) {
            tracer.nextSpan()
        } else {
            tracer.nextSpan(extracted)
        }
    }

    fun extractTraceContext(headers: List<Pair<String, String>>): TraceContextOrSamplingFlags? {
        return recordExtractor.extract(headers)
    }
    fun extractTraceContext(properties: MutableMap<String, Any>): TraceContextOrSamplingFlags? {
        return messageMediatorExtractor.extract(properties)
    }

    fun createBatchPublishTracing(clientId: String): BraveBatchPublishTracing {
        return BraveBatchPublishTracing(clientId, tracer, recordExtractor)
    }
}


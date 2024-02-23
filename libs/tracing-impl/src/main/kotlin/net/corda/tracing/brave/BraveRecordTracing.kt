package net.corda.tracing.brave

import brave.Span
import brave.Tracing
import brave.propagation.TraceContextOrSamplingFlags
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record

class BraveRecordTracing(private val tracing: Tracing) {
    private val tracer = tracing.tracer()
    private val recordExtractor = BraveRecordExtractor(tracing)

    fun getTraceContext(headers: List<Pair<String, String>>): brave.propagation.TraceContext? {
        val extracted = recordExtractor.extract(headers)
        return getTraceContext(extracted)
    }

    fun getTraceContext(headers: Map<String, Any>): brave.propagation.TraceContext? {
        val extracted = recordExtractor.extract(headers)
        return getTraceContext(extracted)
    }

    private fun getTraceContext(extracted: TraceContextOrSamplingFlags?): brave.propagation.TraceContext? {
        return extracted?.context() ?: tracing.currentTraceContext()?.get()
    }

    fun nextSpan(record: Record<*, *>): Span {
        return nextSpan(record.headers)
    }

    fun nextSpan(record: EventLogRecord<*, *>): Span {
        return nextSpan(record.headers)
    }

    fun joinSpan(headers: List<Pair<String, String>>): Span {
        val extracted = recordExtractor.extract(headers)
        return tracer.joinSpan(extracted.context())
    }

    fun nextSpan(headers: List<Pair<String, String>>): Span {
        val extracted = recordExtractor.extract(headers)
        return nextSpan(extracted)
    }

    fun nextSpan(headers: Map<String, Any>): Span {
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


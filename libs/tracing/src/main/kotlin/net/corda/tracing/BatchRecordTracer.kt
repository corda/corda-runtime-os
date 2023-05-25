package net.corda.tracing

import net.corda.messaging.api.records.Record

interface BatchRecordTracer {

    fun startSpanFor(event: Record<*, *>, correlationId: String)

    fun errorSpanFor(correlationId: String, error: Exception, outputRecord: Record<*, *>)

    fun errorSpanFor(correlationId: String, error: Exception, outputRecords: List<Record<*, *>>)

    fun completeSpanFor(correlationId: String, outputRecord: Record<*, *>)

    fun completeSpanFor(correlationId: String, outputRecords: List<Record<*, *>>)
}
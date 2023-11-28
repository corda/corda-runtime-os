package net.corda.tracing

import net.corda.messaging.api.records.Record

interface BatchRecordTracer {

    fun startSpanFor(event: Record<*, *>, correlationId: String)

    fun errorSpanFor(correlationId: String, error: Exception, outputRecord: Record<*, *>): Record<*, *>

    fun completeSpanFor(correlationId: String, outputRecord: Record<*, *>): Record<*, *>
}

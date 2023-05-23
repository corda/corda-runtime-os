package net.corda.messaging.tracing

import net.corda.messagebus.api.tracing.CordaRecordTraceSpan
import net.corda.messaging.api.tracing.RecordTraceSpan

class RecordTraceSpanImpl(private val cordaRecordTraceSpan: CordaRecordTraceSpan) : RecordTraceSpan {
    override fun start() {
        cordaRecordTraceSpan.start()
    }

    override fun error(exception: Exception) {
        cordaRecordTraceSpan.error(exception)
    }

    override fun finish() {
        cordaRecordTraceSpan.finish()
    }
}
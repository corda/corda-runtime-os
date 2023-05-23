package net.corda.messaging.tracing

import net.corda.messagebus.api.tracing.CordaRecordTracingContext
import net.corda.messaging.api.tracing.RecordTraceSpan
import net.corda.messaging.api.tracing.RecordTracingContext

class RecordTracingContextImpl(private val cordaRecordTracingContext: CordaRecordTracingContext) :
    RecordTracingContext {
    override fun nextSpan(operationName: String): RecordTraceSpan {
        return RecordTraceSpanImpl(cordaRecordTracingContext.nextSpan(operationName))
    }

    override fun <T> recordNextSpan(operationName: String, processingBlock: () -> T): T {
        return cordaRecordTracingContext.recordNextSpan(operationName, processingBlock)
    }
}


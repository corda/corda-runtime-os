package net.corda.messagebus.api.tracing

interface CordaRecordTracingContext {
    fun nextSpan(operationName: String): CordaRecordTraceSpan

    fun <T> recordNextSpan(operationName: String, processingBlock: () -> T): T
}
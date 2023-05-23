package net.corda.messaging.api.tracing


interface RecordTracingContext {
    fun nextSpan(operationName: String): RecordTraceSpan

    fun <T> recordNextSpan(operationName: String, processingBlock: () -> T): T
}


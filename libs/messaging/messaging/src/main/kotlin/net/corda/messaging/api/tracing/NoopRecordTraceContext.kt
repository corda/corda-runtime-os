package net.corda.messaging.api.tracing

class NoopRecordTraceContext : RecordTracingContext {

    @Suppress("UNUSED_PARAMETER")
    override fun nextSpan(operationName: String): RecordTraceSpan {
        return NoopRecordTraceSpan()
    }

    @Suppress("UNUSED_PARAMETER")
    override fun <T> recordNextSpan(operationName: String, processingBlock: () -> T): T {
        return processingBlock()
    }

    private class NoopRecordTraceSpan : RecordTraceSpan {
        override fun start() {
        }

        @Suppress("UNUSED_PARAMETER")
        override fun error(exception: Exception) {
        }

        override fun finish() {
        }
    }
}
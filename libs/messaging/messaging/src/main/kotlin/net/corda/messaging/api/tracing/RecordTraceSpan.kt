package net.corda.messaging.api.tracing

interface RecordTraceSpan {

    fun start()

    fun error(exception: Exception)

    fun finish()
}
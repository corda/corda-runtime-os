package net.corda.messagebus.api.tracing

interface CordaRecordTraceSpan {

    fun start()

    fun error(exception: Exception)

    fun finish()
}
package net.corda.tracing.impl

import brave.Span
import net.corda.tracing.TraceContext
import net.corda.tracing.TraceTag

internal class BraveTraceContext(
    private val span: Span
) : TraceContext {

    override fun traceTag(key: String, value: String) {
        span.tag(key, value)
    }

    override fun traceTag(key: TraceTag, value: String) {
        span.tag(key.toString(), value)
    }

    override fun traceRequestId(requestId: String) {
        BraveBaggageFields.REQUEST_ID.updateValue(requestId)
    }

    override fun traceVirtualNodeId(vNodeId: String) {
        BraveBaggageFields.VIRTUAL_NODE_ID.updateValue(vNodeId)
    }

    override fun traceTxId(txId: String) {
        BraveBaggageFields.TRANSACTION_ID.updateValue(txId)
    }
}
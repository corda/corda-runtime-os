package net.corda.tracing.impl

import brave.Span
import brave.Tracer
import net.corda.tracing.TraceContext
import net.corda.tracing.TraceTag

internal class BraveTraceContext(
    private val tracer: Tracer,
    private val span: Span
) : TraceContext {

    override fun traceTag(key: String, value: String) {
        span.tag(key, value)
    }

    override fun traceRequestId(requestId: String) {
        BraveBaggageFields.REQUEST_ID.updateValue(requestId)
        span.tag(TraceTag.FLOW_REQUEST_ID, requestId)
    }

    override fun traceVirtualNodeId(vNodeId: String) {
        BraveBaggageFields.VIRTUAL_NODE_ID.updateValue(vNodeId)
        span.tag(TraceTag.FLOW_REQUEST_VNODE_ID, vNodeId)
    }

    override fun markInScope(): AutoCloseable{
         return tracer.withSpanInScope(span)
    }

    override fun errorAndFinish(e: Exception) {
        span.error(e).finish()
    }

    override fun finish() {
        span.finish()
    }
}
package net.corda.tracing.impl

import brave.Span
import net.corda.tracing.TraceContext
import net.corda.tracing.TraceTag

internal class TraceContextImpl(private val span: Span) : TraceContext {

    override fun traceTag(key: String, value: String) {
        span.tag(key,value)
    }

    override fun traceTag(key: TraceTag, value: String) {
        span.tag(key.toString(),value)
    }

    override fun traceRequestId(requestId:String){
        TracingState.requestId.updateValue(requestId)
    }

    override fun traceVirtualNodeId(vNodeId:String){
        TracingState.virtualNodeId.updateValue(vNodeId)
    }

    override fun traceTxId(txId:String){
        TracingState.virtualNodeId.updateValue(txId)
    }
}
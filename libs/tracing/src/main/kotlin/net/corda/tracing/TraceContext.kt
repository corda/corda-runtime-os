package net.corda.tracing

interface TraceContext {
    fun traceTag(key: String, value: String)

    fun traceRequestId(requestId: String)

    fun traceVirtualNodeId(vNodeId: String)

    fun traceTxId(txId: String)
}
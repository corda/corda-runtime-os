package net.corda.tracing

interface TraceContext {
    fun traceTag(key: String, value: String)

    fun traceRequestId(requestId: String)

    fun traceVirtualNodeId(vNodeId: String)

    fun markInScope(): AutoCloseable

    fun error(e:Exception)

    fun errorAndFinish(e: Exception)

    fun finish()

    val traceIdString: String
}
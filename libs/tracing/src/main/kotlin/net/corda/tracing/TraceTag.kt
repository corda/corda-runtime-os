package net.corda.tracing

/**
 * Tracing objects that will exist for the lifetime of the application.
 *
 * Close before shutdown to wait for trace spans to be sent to external systems.
 */
object TraceTag {
    const val FLOW_CLASS = "flow.class"
    const val FLOW_REQUEST_ID = "flow.request.id"
    const val FLOW_REQUEST_VNODE_ID = "flow.request.vnode.id"
}
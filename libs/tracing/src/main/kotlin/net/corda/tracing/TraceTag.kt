package net.corda.tracing

/**
 * Tracing objects that will exist for the lifetime of the application.
 *
 * Close before shutdown to wait for trace spans to be sent to external systems.
 */
enum class TraceTag {
    FLOW_CLASS,
}
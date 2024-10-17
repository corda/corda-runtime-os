package net.corda.flow.external.events

data class ExternalEventContext(
    val requestId: String,
    val flowId: String,
    val parameters: Map<String, String>
)

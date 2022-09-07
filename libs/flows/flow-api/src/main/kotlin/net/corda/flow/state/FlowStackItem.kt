package net.corda.flow.state

/**
 * The FlowStackItem represents the elements within the flow stack.
 */
data class FlowStackItem(
    val flowName: String,
    val isInitiatingFlow: Boolean,
    val sessionIds: MutableList<String>,
    val contextUserProperties: MutableMap<String, String>,
    val contextPlatformProperties: MutableMap<String, String>
)

package net.corda.messaging.api.mediator

/**
 * Class for storing message data and metadata.
 */
data class Message(
    /** Message body (payload). */
    val body: Any,
    /** Message properties (metadata). */
    val properties: Map<String, Any>,
)

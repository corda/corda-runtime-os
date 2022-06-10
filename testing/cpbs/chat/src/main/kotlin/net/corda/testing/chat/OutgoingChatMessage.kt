package net.corda.testing.chat

/**
 * An outgoing chat message, one being sent by this member to another.
 */
data class OutgoingChatMessage(
    val recipientX500Name: String? = null,
    val message: String? = null
)

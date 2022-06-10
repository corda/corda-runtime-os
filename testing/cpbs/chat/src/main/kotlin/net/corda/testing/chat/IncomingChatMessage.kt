package net.corda.testing.chat

/**
 * An incoming chat message, one received by this member from another.
 */
data class IncomingChatMessage(
    val senderX500Name: String,
    val message: String
)

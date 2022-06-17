package net.cordapp.testing.chat

/**
 * An incoming chat message, one received by this member sent from another.
 */
data class IncomingChatMessage(
    val senderX500Name: String,
    val message: String
)

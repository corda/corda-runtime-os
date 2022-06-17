package net.cordapp.testing.chat

/**
 * A container of received chat messages. Used for JSON encoding.
 */
data class ReceivedChatMessages(
    val messages: List<IncomingChatMessage>
)

package net.cordapp.testing.chat

/**
 * A container of chat messages. Used for JSON encoding.
 */
data class Messages(val receivedChatMessages: ReceivedChatMessages, val sentChatMessages: SentChatMessages)

/**
 * A container of received chat messages. Used for JSON encoding.
 */
data class ReceivedChatMessages(
    val messages: List<IncomingChatMessage>
)

/**
 * A container of sent chat messages. Used for JSON encoding.
 */
data class SentChatMessages(
    val messages: List<OutgoingChatMessage>
)

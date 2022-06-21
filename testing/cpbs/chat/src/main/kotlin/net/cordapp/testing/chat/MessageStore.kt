package net.cordapp.testing.chat

/**
 * Static message store, which holds unread messages in memory. Should be replaced with use of the persistence api when
 * available.
 */
object MessageStore {
    fun add(message: IncomingChatMessage) {
        synchronized(lock) {
            messages.add(message)
        }
    }

    fun readAndClear(): ReceivedChatMessages {
        synchronized(lock) {
            val unreadMessages = messages
            messages = mutableListOf()
            return ReceivedChatMessages(unreadMessages)
        }
    }

    private val lock = Any()
    private var messages = mutableListOf<IncomingChatMessage>()
}

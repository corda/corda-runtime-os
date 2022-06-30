package net.cordapp.testing.chat

import net.corda.v5.application.persistence.PersistenceService

/**
 * MessageStore. Persists messages using the PersistenceService.
 * Very limited implementation, will only store the last message from any sender due to limitations of the persistence
 * api.
 */
object MessageStore {
    fun add(
        persistenceService: PersistenceService,
        incomingMessage: IncomingChatMessage
    ) {
        // Persist the message - only the last message from each sender can be stored due to the current limitations of
        // the persistence api
        // TODO race condition: if multiple incoming messages are processed concurrently, even if this method doesn't
        //  fail, what happens is undefined
        persistenceService.find(IncomingChatMessage::class.java, incomingMessage.name)?.let {
            // There's already a record from this sender, overwrite the last message stored
            // TODO race condition: if readAndClear is called now for this sender, this will fail
            persistenceService.merge(incomingMessage)
        } ?: run {
            // TODO race condition: if another incoming message from this sender is processed here, this will fail
            persistenceService.persist(incomingMessage)
        }
    }

    fun readAndClear(
        persistenceService: PersistenceService,
        sender: String,
    ): ReceivedChatMessages {
        // Find the last message from this sender
        // TODO race condition: if readAndClear is called concurrently, each invocation could return the same message
        val lastMessage = persistenceService.find(IncomingChatMessage::class.java, sender)
        if (lastMessage != null) {
            // TODO race condition: the message being removed may no longer exist if readAndClear was called
            // concurrently - there is no findAndRemove atomic method so this is always unavoidable
            persistenceService.remove(lastMessage)
        }
        return ReceivedChatMessages(messages = listOf(lastMessage!!))
    }
}

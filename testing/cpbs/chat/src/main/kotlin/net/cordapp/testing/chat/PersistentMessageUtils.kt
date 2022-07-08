package net.cordapp.testing.chat

import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable

@Suspendable
fun add(
    persistenceService: PersistenceService,
    incomingMessage: IncomingChatMessage
) {
    // Persist the message - only the last message from each sender can be stored due to the current limitations of
    // the persistence api
    // TODO race condition: if multiple incoming messages are processed concurrently, even if this method doesn't
    //  fail, what happens is undefined
    persistenceService.find(IncomingChatMessage::class.java, incomingMessage.sender)?.let {
        // There's already a record from this sender, overwrite the last message stored
        // TODO race condition: if readAndClear is called now for this sender, this will fail
        persistenceService.merge(incomingMessage)
    } ?: run {
        // TODO race condition: if another incoming message from this sender is processed here, this will fail
        persistenceService.persist(incomingMessage)
    }
}

@Suspendable
fun readAndClear(
    persistenceService: PersistenceService,
    sender: String,
): ReceivedChatMessages {
    // Find the last message from this sender
    // TODO race condition: if readAndClear is called concurrently, each invocation could return the same message
    return ReceivedChatMessages(persistenceService.find(IncomingChatMessage::class.java, sender)?.let { message ->
        // TODO race condition: the message being removed may no longer exist if readAndClear was called concurrently
        persistenceService.remove(message)
        listOf(message)
    } ?: emptyList())
}

@Suspendable
fun readAllAndClear(persistenceService: PersistenceService): ReceivedChatMessages {
    // TODO race condition: the message being removed may no longer exist if readAndClear was called concurrently
    return persistenceService.findAll(IncomingChatMessage::class.java)
        .onEach { message -> persistenceService.remove(message) }.let {
            ReceivedChatMessages(it)
        }
}

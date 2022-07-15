package net.cordapp.testing.chat

import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.persistence.find
import net.corda.v5.application.persistence.findAll
import net.corda.v5.base.annotations.Suspendable
import java.lang.Integer.max
import java.time.Instant
import java.util.UUID

const val MAX_NUMBER_STORED_MESSAGES = 3

@Suspendable
fun storeIncomingMessage(
    persistenceService: PersistenceService,
    sender: String,
    message: String
) {
    val existingMessages = persistenceService.findAll<IncomingChatMessage>()
    trimMessages(existingMessages, persistenceService)
    persistenceService.persist(
        IncomingChatMessage(
            id = newUuid(persistenceService),
            sender = sender,
            message = message,
            timestamp = unixTimestamp()
        )
    )
}

@Suspendable
fun storeOutgoingMessage(
    persistenceService: PersistenceService,
    recipient: String,
    message: String
) {
    val existingMessages = persistenceService.findAll<OutgoingChatMessage>()
    trimMessages(existingMessages, persistenceService)
    persistenceService.persist(
        OutgoingChatMessage(
            id = newUuid(persistenceService),
            recipient = recipient,
            message = message,
            timestamp = unixTimestamp()
        )
    )
}

@Suspendable
fun readAllMessages(persistenceService: PersistenceService): Messages {
    val receivedMessages = persistenceService.findAll<IncomingChatMessage>().let {
        ReceivedChatMessages(it)
    }
    val sentMessages = persistenceService.findAll<OutgoingChatMessage>().let {
        SentChatMessages(it)
    }
    return Messages(sentChatMessages = sentMessages, receivedChatMessages = receivedMessages)
}

@Suspendable
private fun newUuid(persistenceService: PersistenceService): UUID {
    var uuidCandidate: UUID
    var retryCount = 0
    do {
        class UUIDGenerationFailure : Exception("Could not generate unique Id for message")
        if (++retryCount > 10) throw UUIDGenerationFailure()
        uuidCandidate = UUID.randomUUID()
    } while (persistenceService.find<IncomingChatMessage>(uuidCandidate) != null)
    return uuidCandidate
}

private fun unixTimestamp() = Instant.now().epochSecond.toString()

@Suspendable
private fun trimMessages(
    existingMessages: List<*>,
    persistenceService: PersistenceService
) {
    val numberMessagesToRemove = max(0, (existingMessages.size + 1) - MAX_NUMBER_STORED_MESSAGES)
    repeat(numberMessagesToRemove) {
        persistenceService.remove(existingMessages[it] as Any)
    }
}

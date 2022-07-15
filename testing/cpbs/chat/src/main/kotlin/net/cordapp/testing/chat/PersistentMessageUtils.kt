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
fun storeMessage(
    persistenceService: PersistenceService,
    sender: String,
    message: String
) {
    val existingMessages = persistenceService.findAll<IncomingChatMessage>()
    val numberMessagesToRemove = max(0, (existingMessages.size + 1) - MAX_NUMBER_STORED_MESSAGES)
    repeat(numberMessagesToRemove) {
        persistenceService.remove(existingMessages[it])
    }
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
fun readAllMessages(persistenceService: PersistenceService): ReceivedChatMessages =
    persistenceService.findAll<IncomingChatMessage>().let {
        ReceivedChatMessages(it)
    }

@Suspendable
fun newUuid(persistenceService: PersistenceService): UUID {
    var uuidCandidate: UUID
    var retryCount = 0
    do {
        class UUIDGenerationFailure : Exception("Could not generate unique Id for message")
        if (++retryCount > 10) throw UUIDGenerationFailure()
        uuidCandidate = UUID.randomUUID()
    } while (persistenceService.find<IncomingChatMessage>(uuidCandidate) != null)
    return uuidCandidate
}

fun unixTimestamp() = Instant.now().epochSecond.toString()

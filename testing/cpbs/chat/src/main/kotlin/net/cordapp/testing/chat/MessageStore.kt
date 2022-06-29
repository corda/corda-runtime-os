package net.cordapp.testing.chat

import net.corda.v5.application.persistence.PersistenceService
import java.time.Duration

/**
 * MessageStore. Persists messages using the PersistenceService.
 */
object MessageStore {
    fun add(persistenceService: PersistenceService, message: IncomingChatMessage) {
        persistenceService.persist(message)
    }

    fun readAndClear(persistenceService: PersistenceService): ReceivedChatMessages {

        val cursor = persistenceService.query<IncomingChatMessage>("IncomingChatMessage.all", emptyMap())
        val pollResult = cursor.poll(999, Duration.ofSeconds(5))
        // TODO
        //persistenceService.remove(pollResult.values)
        return ReceivedChatMessages(pollResult.values)
    }
}

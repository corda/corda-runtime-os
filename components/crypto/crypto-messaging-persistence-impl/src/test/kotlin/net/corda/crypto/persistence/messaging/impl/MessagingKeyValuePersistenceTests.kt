package net.corda.crypto.persistence.messaging.impl

import net.corda.data.crypto.persistence.SigningKeysRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture

class MessagingKeyValuePersistenceTests {
    @Timeout(10)
    @Test
    fun `Should throw IllegalArgumentException when trying to put records without keys`() {
        val persistence = MessagingKeyValuePersistence(
            processor =  MockMessagingPersistenceProcessor(),
            expireAfterAccessMins = 60,
            maximumSize = 100
        ) { it }
        assertThrows<IllegalArgumentException> {
            persistence.put(SigningKeysRecord())
        }
    }

    class MockMessagingPersistenceProcessor : MessagingPersistenceProcessor<SigningKeysRecord> {
        override fun publish(entity: SigningKeysRecord, vararg key: EntityKeyInfo): List<CompletableFuture<Unit>> {
            val completedFuture = CompletableFuture<Unit>()
            completedFuture.complete(Unit)
            return listOf(completedFuture)
        }

        override fun getValue(key: String): SigningKeysRecord? = null

        override fun close() {
        }
    }
}
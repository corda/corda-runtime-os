package net.corda.crypto.persistence.messaging.impl

import net.corda.data.crypto.persistence.SigningKeysRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class MessagingKeyValuePersistenceTests {
    @Timeout(5)
    @Test
    fun `Should throw IllegalArgumentException when trying to put records without keys`() {
        val persistence = MessagingKeyValuePersistence<SigningKeysRecord, SigningKeysRecord>(
            processor =  mock(),
            expireAfterAccessMins = 60,
            maximumSize = 100
        ) { it }
        assertThrows<IllegalArgumentException> {
            persistence.put(SigningKeysRecord())
        }
    }
}
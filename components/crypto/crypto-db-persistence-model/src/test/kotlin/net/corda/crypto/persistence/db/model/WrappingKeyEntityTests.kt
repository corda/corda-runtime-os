package net.corda.crypto.persistence.db.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

class WrappingKeyEntityTests {
    @Test
    fun `Should equal when alias properties are matching`() {
        val random = Random(Instant.now().toEpochMilli())
        val alias = UUID.randomUUID().toString()
        val e1 = WrappingKeyEntity(
            alias = alias,
            created = Instant.now().minusSeconds(5),
            encodingVersion = 1,
            algorithmName = "AES",
            keyMaterial = random.nextBytes(512)
        )
        val e2 = WrappingKeyEntity(
            alias = alias,
            created = Instant.now().minusSeconds(10),
            encodingVersion = 11,
            algorithmName = "AES2",
            keyMaterial = random.nextBytes(512)
        )
        assertEquals(e1, e2)
    }

    @Test
    fun `Should equal to itself`() {
        val random = Random(Instant.now().toEpochMilli())
        val alias = UUID.randomUUID().toString()
        val e1 = WrappingKeyEntity(
            alias = alias,
            created = Instant.now().minusSeconds(5),
            encodingVersion = 1,
            algorithmName = "AES",
            keyMaterial = random.nextBytes(512)
        )
        assertEquals(e1, e1)
    }

    @Test
    fun `Should not equal when alias properties are not matching`() {
        val random = Random(Instant.now().toEpochMilli())
        val e1 = WrappingKeyEntity(
            alias = UUID.randomUUID().toString(),
            created = Instant.now().minusSeconds(5),
            encodingVersion = 1,
            algorithmName = "AES",
            keyMaterial = random.nextBytes(512)
        )
        val e2 = WrappingKeyEntity(
            alias = UUID.randomUUID().toString(),
            created = Instant.now().minusSeconds(10),
            encodingVersion = 11,
            algorithmName = "AES2",
            keyMaterial = random.nextBytes(512)
        )
        assertNotEquals(e1, e2)
    }

    @Test
    fun `Should not equal to null`() {
        val random = Random(Instant.now().toEpochMilli())
        val e1 = WrappingKeyEntity(
            alias = UUID.randomUUID().toString(),
            created = Instant.now().minusSeconds(5),
            encodingVersion = 1,
            algorithmName = "AES",
            keyMaterial = random.nextBytes(512)
        )
        assertNotEquals(e1, null)
    }
}
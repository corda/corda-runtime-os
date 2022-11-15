package net.corda.v5.cipher.suite

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.SecureRandom
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CryptoServiceUtilsTests {
    companion object {
        private val secureRandom = SecureRandom()

        private fun generateSecret(): ByteArray {
            val bytes = ByteArray(32)
            secureRandom.nextBytes(bytes)
            return bytes
        }
    }

    @Test
    @Suppress("ForEachOnRange")
    fun `computeHSMAlias should throw IllegalArgumentException incorrect take value`() {
        (0..11).forEach {
            assertThrows<IllegalArgumentException> {
                computeHSMAlias(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    generateSecret(),
                    it
                )
            }
        }
        (33..40).forEach {
            assertThrows<IllegalArgumentException> {
                computeHSMAlias(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    generateSecret(),
                    it
                )
            }
        }
    }

    @Test
    @Suppress("ForEachOnRange")
    fun `computeHSMAlias should take specified number of characters`() {
        (12..32).forEach {
            val result = computeHSMAlias(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                generateSecret(),
                it
            )
            assertEquals(it, result.length)
        }
    }

    @Test
    fun `computeHSMAlias should throw IllegalArgumentException for blank tenant id`() {
        assertThrows<IllegalArgumentException> {
            computeHSMAlias(
                "",
                UUID.randomUUID().toString(),
                generateSecret()
            )
        }
    }

    @Test
    fun `computeHSMAlias should throw IllegalArgumentException for blank alias`() {
        assertThrows<IllegalArgumentException> {
            computeHSMAlias(
                UUID.randomUUID().toString(),
                "",
                generateSecret()
            )
        }
    }

    @Test
    fun `computeHSMAlias should throw IllegalArgumentException for empty secret`() {
        assertThrows<IllegalArgumentException> {
            computeHSMAlias(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                ByteArray(0)
            )
        }
    }

    @Test
    fun `computeHSMAlias should compute deterministic output for the same inputs`() {
        val tenant = UUID.randomUUID().toString()
        val alias = UUID.randomUUID().toString()
        val secret = generateSecret()
        val a1 = computeHSMAlias(
            tenant,
            alias,
            secret
        )
        val a2 = computeHSMAlias(
            tenant,
            alias,
            secret
        )
        val a3 = computeHSMAlias(
            tenant,
            alias,
            secret
        )
        assertEquals(12, a1.length)
        assertEquals(12, a2.length)
        assertEquals(a1, a2)
        assertEquals(a1, a3)
    }

    @Test
    fun `computeHSMAlias should compute different output for different tenants`() {
        val alias = UUID.randomUUID().toString()
        val secret = generateSecret()
        val a1 = computeHSMAlias(
            UUID.randomUUID().toString(),
            alias,
            secret
        )
        val a2 = computeHSMAlias(
            UUID.randomUUID().toString(),
            alias,
            secret
        )
        assertEquals(12, a1.length)
        assertEquals(12, a2.length)
        assertNotEquals(a1, a2)
    }

    @Test
    fun `computeHSMAlias should compute different output for different aliases`() {
        val tenant = UUID.randomUUID().toString()
        val secret = generateSecret()
        val a1 = computeHSMAlias(
            tenant,
            UUID.randomUUID().toString(),
            secret
        )
        val a2 = computeHSMAlias(
            tenant,
            UUID.randomUUID().toString(),
            secret
        )
        assertEquals(12, a1.length)
        assertEquals(12, a2.length)
        assertNotEquals(a1, a2)
    }

    @Test
    fun `computeHSMAlias should compute different output for different secrets`() {
        val tenant = UUID.randomUUID().toString()
        val alias = UUID.randomUUID().toString()
        val secret1 = generateSecret()
        val secret2 = generateSecret()
        val a1 = computeHSMAlias(
            tenant,
            alias,
            secret1
        )
        val a2 = computeHSMAlias(
            tenant,
            alias,
            secret2
        )
        assertEquals(12, a1.length)
        assertEquals(12, a2.length)
        assertNotEquals(a1, a2)
    }
}
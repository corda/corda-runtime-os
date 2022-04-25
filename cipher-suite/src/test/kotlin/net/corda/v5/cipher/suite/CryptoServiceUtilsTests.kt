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
        assertNotEquals(a1, a2)
    }
}
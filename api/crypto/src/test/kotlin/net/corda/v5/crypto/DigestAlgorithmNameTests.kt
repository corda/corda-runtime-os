package net.corda.v5.crypto

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DigestAlgorithmNameTests {
    @Test
    fun `equal should return false when comparing to null`() {
        assertFalse(DigestAlgorithmName.SHA2_256.equals(null))
    }

    @Test
    fun `equal should return false when comparing to other types`() {
        assertFalse(DigestAlgorithmName.SHA2_256.equals(DigestAlgorithmName.SHA2_256.name))
        assertFalse(DigestAlgorithmName.SHA2_256.equals(Any()))
    }

    @Test
    fun `equal should return true when comparing to itself`() {
        assertTrue(DigestAlgorithmName.SHA2_256.equals(DigestAlgorithmName.SHA2_256))
    }

    @Test
    fun `equal should return true when comparing to the equal value`() {
        assertTrue(DigestAlgorithmName.SHA2_256.equals(DigestAlgorithmName("SHA-256")))
    }

    @Test
    fun `equal should return true when comparing to the value with different casing`() {
        assertTrue(DigestAlgorithmName.SHA2_256.equals(DigestAlgorithmName("sha-256")))
    }

    @Test
    fun `hasCode should be the same value for the same values`() {
        assertEquals(DigestAlgorithmName.SHA2_256.hashCode(), DigestAlgorithmName.SHA2_256.hashCode())
    }

    @Test
    fun `hasCode should be the same value for the same values with different casing`() {
        assertEquals(DigestAlgorithmName.SHA2_256.hashCode(), DigestAlgorithmName("sha-256").hashCode())
    }

    @Test
    fun `hasCode should be different value for the different values`() {
        assertNotEquals(DigestAlgorithmName.SHA2_256.hashCode(), DigestAlgorithmName.SHA2_384.hashCode())
    }

    @Test
    fun `toString should return the wrapped algorithm name`() {
        assertEquals(DigestAlgorithmName.SHA2_256.name, DigestAlgorithmName.SHA2_256.toString())
    }
}
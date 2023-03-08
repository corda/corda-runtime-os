package net.corda.crypto.core

import net.corda.v5.crypto.DigestAlgorithmName
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SecureHashImplTests {
    @Test
    fun `toHexString Should HEX representation of digest`() {
        val data = "abc".toByteArray()
        val algorithm = DigestAlgorithmName.SHA2_384.name
        val digest = MessageDigest.getInstance(algorithm).digest(data)
        val cut = SecureHashImpl(algorithm, digest)
        assertEquals("CB00753F45A35E8BB5A03D699AC65007272C32AB0EDED1631A8B605A43FF5BED8086072BA1E7CC2358BAECA134C825A7", cut.toHexString())
    }

    @Test
    fun `toString Should output string with algorithm name, delimiter and HEX representation of digest`() {
        val data = "Hello World!".toByteArray()
        val algorithm = DigestAlgorithmName.SHA2_384.name
        val digest = MessageDigest.getInstance(algorithm).digest(data)
        val cut = SecureHashImpl(algorithm, digest)
        assertEquals(
            "SHA-384:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FD6FD061F9D5CFD13B8F961358E6ADBA4A",
            cut.toString()
        )
    }

    @Test
    fun `prefixChars should output request first N characters of HEX representation of digest`() {
        val data = "def".toByteArray()
        val algorithm = DigestAlgorithmName.SHA2_384.name
        val digest = MessageDigest.getInstance(algorithm).digest(data)
        val cut = SecureHashImpl(algorithm, digest)
        assertEquals("180C325CCC", cut.prefixChars(10))
    }

    @Test
    fun `Should create instance out of proper formatted string with algorithm, delimiter and HEX representation of digest`() {
        val str = "SHA-384:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FD6FD061F9D5CFD13B8F961358E6ADBA4A"
        val expectedBytes = byteArrayOf(
            -65, -41, 108, 14, -69, -48, 6, -2, -27, -125, 65, 5, 71, -63, -120, 123, 2, -110, -66, 118, -43, -126, -39, 108,
            36, 45, 42, 121, 39, 35, -29, -3, 111, -48, 97, -7, -43, -49, -47, 59, -113, -106, 19, 88, -26, -83, -70, 74
        )
        val cut = parseSecureHash(str)
        assertEquals(DigestAlgorithmName.SHA2_384.name, cut.getAlgorithm())
        assertArrayEquals(expectedBytes, cut.bytes)
    }

    @Test
    fun `Should throw IllegalArgumentException when create does not have input with correct delimiter`() {
        val str = "SHA-384!BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FD6FD061F9D5CFD13B8F961358E6ADBA4A"
        assertFailsWith(IllegalArgumentException::class) {
            parseSecureHash(str)
        }
    }

    @Test
    fun `Two instances created from same digests should be equal`() {
        val data = "abc".toByteArray()
        val algorithm = DigestAlgorithmName.SHA2_384.name
        val digest1 = MessageDigest.getInstance(algorithm).digest(data)
        val digest2 = MessageDigest.getInstance(algorithm).digest(data)
        val cut1 = SecureHashImpl(algorithm, digest1)
        val cut2 = SecureHashImpl(algorithm, digest2)
        assertEquals(cut1, cut2)
    }

    @Test
    fun `Two instances created from different digests should not be equal`() {
        val data1 = "abc".toByteArray()
        val data2 = "def".toByteArray()
        val algorithm = DigestAlgorithmName.SHA2_384.name
        val digest1 = MessageDigest.getInstance(algorithm).digest(data1)
        val digest2 = MessageDigest.getInstance(algorithm).digest(data2)
        val cut1 = SecureHashImpl(algorithm, digest1)
        val cut2 = SecureHashImpl(algorithm, digest2)
        assertNotEquals(cut1, cut2)
    }

    @Test
    fun `Two instances created from same digests but different algorithm should not be equal`() {
        val data = "abc".toByteArray()
        val algorithm = DigestAlgorithmName.SHA2_384.name
        val digest1 = MessageDigest.getInstance(algorithm).digest(data)
        val digest2 = MessageDigest.getInstance(algorithm).digest(data)
        val cut1 = SecureHashImpl(algorithm, digest1)
        val cut2 = SecureHashImpl("SHA2-384", digest2)
        assertNotEquals(cut1, cut2)
    }

    @Test
    fun `Comparing to other type than SecureHash should not be equal`() {
        val data = "abc".toByteArray()
        val algorithm = DigestAlgorithmName.SHA2_384.name
        val digest = MessageDigest.getInstance(algorithm).digest(data)
        val cut = SecureHashImpl(algorithm, digest)
        assertFalse(cut.equals(digest))
    }
}
package net.corda.v5.crypto

import net.corda.v5.crypto.mocks.DigestServiceMock
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DigestServiceUtilsTests {
    companion object {
        private lateinit var digestService: DigestService

        @BeforeAll
        @JvmStatic
        fun setup() {
            digestService = DigestServiceMock()
        }

        @JvmStatic
        fun algorithms(): List<Arguments> = listOf(
            Arguments.of(DigestAlgorithmName.SHA2_256, 32),
            Arguments.of(DigestAlgorithmName.SHA2_384, 48),
            Arguments.of(DigestAlgorithmName.SHA2_512, 64)
        )
    }

    @Test
    fun `Should create instance out of proper formatted string with algorithm, delimiter and HEX representation of digest`() {
        val str = "SHA-384:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FD6FD061F9D5CFD13B8F961358E6ADBA4A"
        val expectedBytes = byteArrayOf(
            -65, -41, 108, 14, -69, -48, 6, -2, -27, -125, 65, 5, 71, -63, -120, 123, 2, -110, -66, 118, -43, -126, -39, 108,
            36, 45, 42, 121, 39, 35, -29, -3, 111, -48, 97, -7, -43, -49, -47, 59, -113, -106, 19, 88, -26, -83, -70, 74
        )
        val cut = digestService.create(str)
        assertEquals(DigestAlgorithmName.SHA2_384.name, cut.algorithm)
        assertArrayEquals(expectedBytes, cut.bytes)
    }

    @Test
    fun `Should throw IllegalArgumentException when create does not have input with correct delimiter`() {
        val str = "SHA-384!BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FD6FD061F9D5CFD13B8F961358E6ADBA4A"
        assertFailsWith(IllegalArgumentException::class) {
            digestService.create(str)
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when create does not have input with known algorithm`() {
        val str = "ABCXYZ-42:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FD6FD061F9D5CFD13B8F961358E6ADBA4A"
        assertFailsWith(IllegalArgumentException::class) {
            digestService.create(str)
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when create does not have input with correct length`() {
        val str = "SHA-384:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E"
        assertFailsWith(IllegalArgumentException::class) {
            digestService.create(str)
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when create does not have input with digets`() {
        val str = "SHA-384:"
        assertFailsWith(IllegalArgumentException::class) {
            digestService.create(str)
        }
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    fun `Should return expected getZeroHash for digest length`(algorithm: DigestAlgorithmName, expectedLength: Int) {
        val hash = digestService.getZeroHash(algorithm)
        assertEquals(algorithm.name, hash.algorithm)
        assertArrayEquals(ByteArray(expectedLength) { 0.toByte() }, hash.bytes)
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    fun `Should return expected getAllOnesHash for digest length`(algorithm: DigestAlgorithmName, expectedLength: Int) {
        val hash = digestService.getAllOnesHash(algorithm)
        assertEquals(algorithm.name, hash.algorithm)
        assertArrayEquals(ByteArray(expectedLength) { 255.toByte() }, hash.bytes)
    }
}
package net.corda.v5.application.crypto

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.Arguments
import java.io.InputStream
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HashingServiceUtilsTest {
    companion object {
        private lateinit var digestService: HashingService

        @BeforeAll
        @JvmStatic
        fun setup() {
            digestService = HashingServiceMock()
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
        val cut = digestService.parse(str)
        assertEquals(DigestAlgorithmName.SHA2_384.name, cut.algorithm)
        Assertions.assertArrayEquals(expectedBytes, cut.bytes)
    }

    @Test
    fun `Should throw IllegalArgumentException when create does not have input with correct delimiter`() {
        val str = "SHA-384!BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FD6FD061F9D5CFD13B8F961358E6ADBA4A"
        assertFailsWith(IllegalArgumentException::class) {
            digestService.parse(str)
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when create does not have input with known algorithm`() {
        val str = "ABCXYZ-42:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FD6FD061F9D5CFD13B8F961358E6ADBA4A"
        assertFailsWith(IllegalArgumentException::class) {
            digestService.parse(str)
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when create does not have input with correct length`() {
        val str = "SHA-384:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E"
        assertFailsWith(IllegalArgumentException::class) {
            digestService.parse(str)
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when create does not have input with digest`() {
        val str = "SHA-384:"
        assertFailsWith(IllegalArgumentException::class) {
            digestService.parse(str)
        }
    }

    class HashingServiceMock : HashingService {
        override val defaultDigestAlgorithmName: DigestAlgorithmName
            get() = DigestAlgorithmName.SHA2_256

        override fun hash(bytes: ByteArray, digestAlgorithmName: DigestAlgorithmName): SecureHash =
            SecureHash(digestAlgorithmName.name, MessageDigest.getInstance(digestAlgorithmName.name).digest(bytes))

        override fun hash(inputStream: InputStream, digestAlgorithmName: DigestAlgorithmName): SecureHash {
            val messageDigest = MessageDigest.getInstance(digestAlgorithmName.name)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while(true) {
                val read = inputStream.read(buffer)
                if(read <= 0) break
                messageDigest.update(buffer, 0, read)
            }
            return SecureHash(digestAlgorithmName.name, messageDigest.digest())
        }

        override fun digestLength(digestAlgorithmName: DigestAlgorithmName): Int =
            MessageDigest.getInstance(digestAlgorithmName.name).digestLength
    }
}
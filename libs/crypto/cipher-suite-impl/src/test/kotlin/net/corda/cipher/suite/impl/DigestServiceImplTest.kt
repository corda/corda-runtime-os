package net.corda.cipher.suite.impl

import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.core.DigestAlgorithmFactoryProvider
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.v5.base.util.ByteArrays
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.InputStream
import kotlin.random.Random

// DigestServiceImpl is basically a wrapper around `PlatformDigestServiceImpl` so main hashing functionality
// is tested in `PlatformDigestServiceImplTest`. Here we will only assert wrapping behavior
// i.e. it looks for custom digest algorithms if not found in platform ones.
class DigestServiceImplTest {
    private companion object {
        val DUMMY_DIGEST = "DUMMY_DIGEST_NAME"
        val DUMMY_DIGEST_LENGTH = 32
        val DUMMY_BYTES = byteArrayOf(0x01, 0x02, 0x03)
    }

    private val platformDigestService = mock<PlatformDigestService>().also {
        // DigestService throwing to impersonate digest algorithm not found in platform digest algorithms
        whenever(it.hash(any<InputStream>(), any())).thenThrow(IllegalArgumentException())
        whenever(it.hash(any<ByteArray>(), any())).thenThrow(IllegalArgumentException())
        whenever(it.digestLength(any())).thenThrow(IllegalArgumentException())
        whenever(it.parseSecureHash(any())).thenThrow(IllegalArgumentException())
    }

    private lateinit var customFactoriesProvider: DigestAlgorithmFactoryProvider
    private lateinit var digestService: DigestServiceImpl

    @BeforeEach
    fun setUp() {
        customFactoriesProvider = mock()
        digestService = DigestServiceImpl(platformDigestService, customFactoriesProvider)
    }

    @Test
    fun `throws IllegalArgumentException, if digest algorithm not found`() {
        assertThrows<IllegalArgumentException> {
            digestService.hash(DUMMY_BYTES, DigestAlgorithmName(DUMMY_DIGEST))
        }
        assertThrows<IllegalArgumentException> {
            digestService.hash(DUMMY_BYTES.inputStream(), DigestAlgorithmName(DUMMY_DIGEST))
        }
        assertThrows<IllegalArgumentException> {
            digestService.digestLength(DigestAlgorithmName(DUMMY_DIGEST))
        }
    }

    @Test
    fun `looks for custom digest algorithm if not found in platform digest algorithms`() {
        val digestAlgorithmFactory = object : DigestAlgorithmFactory {
            override fun getAlgorithm() = DUMMY_DIGEST
            override fun getInstance() =
                object : DigestAlgorithm {
                    override fun getAlgorithm() = DUMMY_DIGEST
                    override fun getDigestLength() = DUMMY_DIGEST_LENGTH
                    override fun digest(inputStream: InputStream) = DUMMY_BYTES
                    override fun digest(bytes: ByteArray) = DUMMY_BYTES
                }
        }
        whenever(customFactoriesProvider.get(DUMMY_DIGEST)).thenReturn(digestAlgorithmFactory)

        val hash0 = digestService.hash(DUMMY_BYTES, DigestAlgorithmName(DUMMY_DIGEST))
        val hash1 = digestService.hash(DUMMY_BYTES.inputStream(), DigestAlgorithmName(DUMMY_DIGEST))
        val hashLength = digestService.digestLength(DigestAlgorithmName(DUMMY_DIGEST))

        val expectedDummySecureHash = SecureHashImpl(DUMMY_DIGEST, DUMMY_BYTES)
        assertEquals(expectedDummySecureHash, hash0)
        assertEquals(expectedDummySecureHash, hash1)
        assertEquals(DUMMY_DIGEST_LENGTH, hashLength)
    }

    @Test
    fun `parses secure hash of custom digest algorithm`() {
        val digestAlgorithmFactory = object : DigestAlgorithmFactory {
            override fun getAlgorithm() = "CUSTOM_DIGEST"
            override fun getInstance() =
                object : DigestAlgorithm {
                    override fun getAlgorithm() = "CUSTOM_DIGEST"
                    override fun getDigestLength() = 64
                    override fun digest(inputStream: InputStream) = throw UnsupportedOperationException()
                    override fun digest(bytes: ByteArray) = throw UnsupportedOperationException()
                }
        }
        whenever(customFactoriesProvider.get("CUSTOM_DIGEST")).thenReturn(digestAlgorithmFactory)

        val hashBytes = Random.nextBytes(64)
        val algoNameAndHexString = "CUSTOM_DIGEST${SecureHash.DELIMITER}${ByteArrays.toHexString(hashBytes)}"
        val secureHash = digestService.parseSecureHash(algoNameAndHexString)
        assertEquals("CUSTOM_DIGEST", secureHash.algorithm)
        assertTrue(hashBytes.contentEquals(secureHash.bytes))
    }
}

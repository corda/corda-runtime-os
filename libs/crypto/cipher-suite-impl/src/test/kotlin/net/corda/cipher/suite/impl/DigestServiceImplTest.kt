package net.corda.cipher.suite.impl

import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.core.DigestAlgorithmFactoryProvider
import net.corda.crypto.core.SecureHashImpl
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.InputStream

// DigestServiceImpl is basically a wrapper around `PlatformDigestServiceImpl` so main hashing functionality
// is tested in `PlatformDigestServiceImplTest`. Here we will only assert wrapping behavior
// i.e. it looks for custom digest algorithms if not found in platform ones.
class DigestServiceImplTest {
    private companion object {
        val DUMMY_DIGEST = "DUMMY_DIGEST_NAME"
        val DUMMY_DIGEST_LENGTH = 32
        val DUMMY_BYTES = byteArrayOf(0x01, 0x02, 0x03)
    }

    private val digestService = mock<PlatformDigestService>().also {
        // DigestService throwing to impersonate digest algorithm not found in platform digest algorithms
        whenever(it.hash(any<InputStream>(), any())).thenThrow(IllegalArgumentException())
        whenever(it.hash(any<ByteArray>(), any())).thenThrow(IllegalArgumentException())
        whenever(it.digestLength(any())).thenThrow(IllegalArgumentException())
    }

    private lateinit var customFactoriesProvider: DigestAlgorithmFactoryProvider
    private lateinit var digestServiceImpl: DigestServiceImpl

    @BeforeEach
    fun setUp() {
        customFactoriesProvider = mock()
        digestServiceImpl = DigestServiceImpl(digestService, customFactoriesProvider)
    }

    @Test
    fun `throws IllegalArgumentException, if digest algorithm not found`() {
        assertThrows<IllegalArgumentException> {
            digestServiceImpl.hash(DUMMY_BYTES, DigestAlgorithmName(DUMMY_DIGEST))
        }
        assertThrows<IllegalArgumentException> {
            digestServiceImpl.hash(DUMMY_BYTES.inputStream(), DigestAlgorithmName(DUMMY_DIGEST))
        }
        assertThrows<IllegalArgumentException> {
            digestServiceImpl.digestLength(DigestAlgorithmName(DUMMY_DIGEST))
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

        val hash0 = digestServiceImpl.hash(DUMMY_BYTES, DigestAlgorithmName(DUMMY_DIGEST))
        val hash1 = digestServiceImpl.hash(DUMMY_BYTES.inputStream(), DigestAlgorithmName(DUMMY_DIGEST))
        val hashLength = digestServiceImpl.digestLength(DigestAlgorithmName(DUMMY_DIGEST))

        val expectedDummySecureHash = SecureHashImpl(DUMMY_DIGEST, DUMMY_BYTES)
        assertEquals(expectedDummySecureHash, hash0)
        assertEquals(expectedDummySecureHash, hash1)
        assertEquals(DUMMY_DIGEST_LENGTH, hashLength)
    }
}
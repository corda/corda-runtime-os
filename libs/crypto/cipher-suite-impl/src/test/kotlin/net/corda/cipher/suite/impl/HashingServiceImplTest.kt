package net.corda.cipher.suite.impl

import java.io.InputStream
import net.corda.crypto.core.DigestAlgorithmFactoryProvider
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

// HashingServiceImpl is basically a wrapper around DigestService so main hashing functionality is tested
// in DigestServiceTests. Here we will only test it looks for custom digest algorithms if not found
// in platform ones.
class HashingServiceImplTest {
    private companion object {
        val DUMMY_DIGEST = "DUMMY_DIGEST_NAME"
        val DUMMY_DIGEST_LENGTH = 32
        val DUMMY_BYTES = byteArrayOf(0x01, 0x02, 0x03)
    }

    private val digestService = mock<DigestService>().also {
        // DigestService throwing to impersonate digest algorithm not found in platform digest algorithms
        whenever(it.hash(any<InputStream>(), any())).thenThrow(IllegalArgumentException())
        whenever(it.hash(any<ByteArray>(), any())).thenThrow(IllegalArgumentException())
        whenever(it.digestLength(any())).thenThrow(IllegalArgumentException())
    }

    private lateinit var customFactoriesProvider: DigestAlgorithmFactoryProvider
    private lateinit var hashingServiceImpl: HashingServiceImpl

    @BeforeEach
    fun setUp() {
        customFactoriesProvider = mock()
        hashingServiceImpl = HashingServiceImpl(digestService, customFactoriesProvider)
    }

    @Test
    fun `throws IllegalArgumentException, if digest algorithm not found`() {
        assertThrows<IllegalArgumentException> {
            hashingServiceImpl.hash(DUMMY_BYTES, DigestAlgorithmName(DUMMY_DIGEST))
        }
        assertThrows<IllegalArgumentException> {
            hashingServiceImpl.hash(DUMMY_BYTES.inputStream(), DigestAlgorithmName(DUMMY_DIGEST))
        }
        assertThrows<IllegalArgumentException> {
            hashingServiceImpl.digestLength(DigestAlgorithmName(DUMMY_DIGEST))
        }
    }

    @Test
    fun `looks for custom digest algorithm if not found in platform digest algorithms`() {
        val digestAlgorithmFactory = object : DigestAlgorithmFactory {
            override val algorithm = DUMMY_DIGEST
            override fun getInstance() =
                object : DigestAlgorithm {
                    override val algorithm = DUMMY_DIGEST
                    override val digestLength = DUMMY_DIGEST_LENGTH
                    override fun digest(inputStream: InputStream) = DUMMY_BYTES
                    override fun digest(bytes: ByteArray) = DUMMY_BYTES
                }
        }
        whenever(customFactoriesProvider.get(DUMMY_DIGEST)).thenReturn(digestAlgorithmFactory)

        val hash0 = hashingServiceImpl.hash(DUMMY_BYTES, DigestAlgorithmName(DUMMY_DIGEST))
        val hash1 = hashingServiceImpl.hash(DUMMY_BYTES.inputStream(), DigestAlgorithmName(DUMMY_DIGEST))
        val hashLength = hashingServiceImpl.digestLength(DigestAlgorithmName(DUMMY_DIGEST))

        val expectedDummySecureHash = SecureHash(DUMMY_DIGEST, DUMMY_BYTES)
        assertEquals(expectedDummySecureHash, hash0)
        assertEquals(expectedDummySecureHash, hash1)
        assertEquals(DUMMY_DIGEST_LENGTH, hashLength)
    }
}
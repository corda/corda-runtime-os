package net.corda.cipher.suite.providers

import net.corda.crypto.DigestAlgorithmFactoryProvider
import net.corda.crypto.impl.components.CipherSuiteFactoryImpl
import net.corda.crypto.impl.DoubleSHA256Digest
import net.corda.v5.cipher.suite.DigestAlgorithm
import net.corda.v5.cipher.suite.DigestAlgorithmFactory
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.sha256Bytes
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.mock
import java.io.InputStream
import java.util.UUID
import kotlin.test.assertEquals

class DigestServiceProviderTests {
    companion object {
        private const val CUSTOM_DIGEST = "CUSTOM-SHA256-3"
    }
    private lateinit var schemeMetadataFactory: CipherSuiteFactoryImpl
    private lateinit var provider: DigestServiceProvider
    private lateinit var algorithmFactory: DigestAlgorithmFactory
    private lateinit var factories: DigestAlgorithmFactoryProvider

    @BeforeEach
    fun setup() {
        schemeMetadataFactory = CipherSuiteFactoryImpl()
        schemeMetadataFactory.schemeMetadataProvider = CipherSchemeMetadataProviderImpl()
        algorithmFactory = mock {
            on { algorithm }.thenReturn(CUSTOM_DIGEST)
            on { getInstance() }.thenReturn(object : DigestAlgorithm {
                override val algorithm: String = CUSTOM_DIGEST
                override val digestLength: Int = 32
                override fun digest(inputStream: InputStream): ByteArray =
                    throw NotImplementedError("Not yet implemented")
                override fun digest(bytes: ByteArray): ByteArray =
                    bytes.sha256Bytes().sha256Bytes().sha256Bytes()

            })
        }
        factories = mock {
            on { get(CUSTOM_DIGEST) }.thenReturn(algorithmFactory)
        }
        provider = DigestServiceProviderImpl().also {
            it.customFactoriesProvider = factories
        }
    }

    @Test
    @Timeout(5)
    fun `Should provide configured service instance`() {
        val digest = provider.getInstance(schemeMetadataFactory)
        val data = UUID.randomUUID().toString().toByteArray()
        val algorithm1 = DigestAlgorithmName(CUSTOM_DIGEST)
        assertEquals(32, digest.digestLength(algorithm1))
        assertArrayEquals(data.sha256Bytes().sha256Bytes().sha256Bytes(), digest.hash(data, algorithm1).bytes)
        val algorithm2 = DigestAlgorithmName(DoubleSHA256Digest.ALGORITHM)
        assertEquals(32, digest.digestLength(algorithm2))
        assertArrayEquals(data.sha256Bytes().sha256Bytes(), digest.hash(data, algorithm2).bytes)
    }
}
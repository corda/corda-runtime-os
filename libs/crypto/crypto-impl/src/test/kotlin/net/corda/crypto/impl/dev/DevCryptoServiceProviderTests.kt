package net.corda.crypto.impl.dev

import net.corda.crypto.CryptoCategories
import net.corda.crypto.impl.stubs.MockCryptoFactory
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DevCryptoServiceProviderTests {
    private val masterKeyAlias = "wrapping-key-alias"
    private lateinit var memberId: String
    private lateinit var mockFactory: MockCryptoFactory
    private lateinit var schemeMetadata: CipherSchemeMetadata
    private lateinit var signatureVerifier: SignatureVerificationService

    @BeforeEach
    fun setup() {
        memberId = UUID.randomUUID().toString()
        mockFactory = MockCryptoFactory()
        schemeMetadata = mockFactory.schemeMetadata
        signatureVerifier = mockFactory.createVerificationService()
    }

    @Test
    @Timeout(30)
    fun `Should be able to create instance and use it to generate and sign using all supported schemes`() {
        val cryptoService = createCryptoServiceProvider().createCryptoService(CryptoCategories.LEDGER)
        cryptoService.supportedSchemes().forEach { signatureScheme ->
            val testData = UUID.randomUUID().toString().toByteArray()
            val badVerifyData = UUID.randomUUID().toString().toByteArray()
            val alias = newAlias()
            val publicKey = cryptoService.generateKeyPair(alias, signatureScheme, emptyMap())
            val signature = cryptoService.sign(alias, signatureScheme, testData, emptyMap())
            assertNotNull(publicKey)
            assertTrue(signatureVerifier.isValid(publicKey, signature, testData))
            assertFalse(signatureVerifier.isValid(publicKey, signature, badVerifyData))
        }
    }

    @Test
    @Timeout(30)
    fun `Should be able to create instance and use it to generate and sign using all supported wrapping schemes`() {
        val cryptoService = createCryptoServiceProvider().createCryptoService(CryptoCategories.FRESH_KEYS)
        cryptoService.createWrappingKey(masterKeyAlias, false)
        cryptoService.supportedWrappingSchemes().forEach { signatureScheme ->
            val testData = UUID.randomUUID().toString().toByteArray()
            val badVerifyData = UUID.randomUUID().toString().toByteArray()
            val wrappedKeyPair = cryptoService.generateWrappedKeyPair(masterKeyAlias, signatureScheme, emptyMap())
            assertNotNull(wrappedKeyPair)
            assertNotNull(wrappedKeyPair.publicKey)
            assertNotNull(wrappedKeyPair.keyMaterial)
            val signature = cryptoService.sign(
                WrappedPrivateKey(
                    keyMaterial = wrappedKeyPair.keyMaterial,
                    masterKeyAlias = masterKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = wrappedKeyPair.encodingVersion
                ),
                testData,
                emptyMap()
            )
            assertNotNull(signature)
            assertTrue(signature.isNotEmpty())
            assertTrue(signatureVerifier.isValid(wrappedKeyPair.publicKey, signature, testData))
            assertFalse(signatureVerifier.isValid(wrappedKeyPair.publicKey, signature, badVerifyData))
        }
    }

    @Test
    @Timeout(30)
    fun `Should throw unrecoverable CryptoServiceLibraryException if there is no InMemoryPersistentCacheFactory`() {
        val provider = DevCryptoServiceProvider(
            persistentCacheFactories = listOf(mock())
        )
        val exception = assertThrows<CryptoServiceLibraryException> {
            provider.createCryptoService(CryptoCategories.FRESH_KEYS)
        }
        assertFalse(exception.isRecoverable)
    }

    private fun newAlias(): String = UUID.randomUUID().toString()

    private fun createCryptoServiceProvider(): DevCryptoServiceProvider {
        return DevCryptoServiceProvider(
            persistentCacheFactories = listOf(InMemoryKeyValuePersistenceFactory())
        )
    }

    private fun DevCryptoServiceProvider.createCryptoService(category: String): CryptoService = getInstance(
        CryptoServiceContext(
            memberId = memberId,
            category = category,
            cipherSuiteFactory = mockFactory.cipherSuiteFactory,
            config = DevCryptoServiceConfiguration()
        )
    )
}

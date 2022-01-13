package net.corda.crypto.service.dev

import net.corda.crypto.CryptoConsts
import net.corda.crypto.service.CryptoServicesTestFactory
import net.corda.crypto.service.persistence.InMemoryKeyValuePersistenceFactory
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
    private lateinit var factory: CryptoServicesTestFactory
    private lateinit var services: CryptoServicesTestFactory.CryptoServices
    private lateinit var schemeMetadata: CipherSchemeMetadata
    private lateinit var verifier: SignatureVerificationService

    @BeforeEach
    fun setup() {
        factory = CryptoServicesTestFactory()
        services = factory.createCryptoServices()
        schemeMetadata = factory.getSchemeMap()
        verifier = factory.getSignatureVerificationService()
    }

    @Test
    @Timeout(30)
    fun `Should be able to create instance and use it to generate and sign using all supported schemes`() {
        val cryptoService = createCryptoServiceProvider().createCryptoService(CryptoConsts.CryptoCategories.LEDGER)
        cryptoService.supportedSchemes().forEach { signatureScheme ->
            val testData = UUID.randomUUID().toString().toByteArray()
            val badVerifyData = UUID.randomUUID().toString().toByteArray()
            val alias = newAlias()
            val publicKey = cryptoService.generateKeyPair(alias, signatureScheme, emptyMap())
            val signature = cryptoService.sign(alias, signatureScheme, testData, emptyMap())
            assertNotNull(publicKey)
            assertTrue(verifier.isValid(publicKey, signature, testData))
            assertFalse(verifier.isValid(publicKey, signature, badVerifyData))
        }
    }

    @Test
    @Timeout(30)
    fun `Should be able to create instance and use it to generate and sign using all supported wrapping schemes`() {
        val cryptoService = createCryptoServiceProvider().createCryptoService(CryptoConsts.CryptoCategories.FRESH_KEYS)
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
            assertTrue(verifier.isValid(wrappedKeyPair.publicKey, signature, testData))
            assertFalse(verifier.isValid(wrappedKeyPair.publicKey, signature, badVerifyData))
        }
    }

    @Test
    @Timeout(30)
    fun `Should throw unrecoverable CryptoServiceLibraryException if there is no InMemoryPersistentCacheFactory`() {
        val provider = DevCryptoServiceProvider()
        provider.persistenceFactories = listOf(mock())
        val exception = assertThrows<CryptoServiceLibraryException> {
            provider.createCryptoService(CryptoConsts.CryptoCategories.FRESH_KEYS)
        }
        assertFalse(exception.isRecoverable)
    }

    private fun newAlias(): String = UUID.randomUUID().toString()

    private fun createCryptoServiceProvider(): DevCryptoServiceProvider {
        return DevCryptoServiceProvider().also {
            it.persistenceFactories = listOf(InMemoryKeyValuePersistenceFactory())
        }
    }

    private fun DevCryptoServiceProvider.createCryptoService(category: String): CryptoService = getInstance(
        CryptoServiceContext(
            memberId = services.tenantId,
            category = category,
            cipherSuiteFactory = factory,
            config = DevCryptoServiceConfig()
        )
    )
}

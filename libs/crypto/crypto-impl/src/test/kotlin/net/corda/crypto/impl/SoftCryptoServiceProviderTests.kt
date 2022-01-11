package net.corda.crypto.impl

import net.corda.crypto.CryptoConsts
import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.impl.dev.InMemoryKeyValuePersistenceFactory
import net.corda.crypto.impl.soft.SoftCryptoServiceConfig
import net.corda.crypto.impl.soft.SoftCryptoServiceProvider
import net.corda.crypto.impl.stubs.CryptoServicesTestFactory
import net.corda.test.util.createTestCase
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
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SoftCryptoServiceProviderTests {
    private val masterKeyAlias = "wrapping-key-alias"
    private lateinit var factory: CryptoServicesTestFactory
    private lateinit var services: CryptoServicesTestFactory.CryptoServices
    private lateinit var schemeMetadata: CipherSchemeMetadata
    private lateinit var signatureVerifier: SignatureVerificationService

    @BeforeEach
    fun setup() {
        factory = CryptoServicesTestFactory()
        services = factory.createCryptoServices()
        schemeMetadata = factory.schemeMetadata
        signatureVerifier = factory.verifier
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
            assertTrue(signatureVerifier.isValid(publicKey, signature, testData))
            assertFalse(signatureVerifier.isValid(publicKey, signature, badVerifyData))
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
            assertTrue(signatureVerifier.isValid(wrappedKeyPair.publicKey, signature, testData))
            assertFalse(signatureVerifier.isValid(wrappedKeyPair.publicKey, signature, badVerifyData))
        }
    }

    @Test
    @Timeout(30)
    fun `Should throw unrecoverable CryptoServiceLibraryException if configured factory is not found`() {
        val provider = SoftCryptoServiceProvider()
        provider.persistenceFactories = listOf(InMemoryKeyValuePersistenceFactory())
        provider.start()
        provider.handleConfigEvent(
            CryptoLibraryConfigImpl(
                mapOf(
                    "defaultCryptoService" to emptyMap<String, String>(),
                    "publicKeys" to emptyMap()
                )
            )
        )
        val exception = assertThrows<CryptoServiceLibraryException> {
            provider.createCryptoService(CryptoConsts.CryptoCategories.FRESH_KEYS)
        }
        assertFalse(exception.isRecoverable)
    }

    @Test
    @Timeout(30)
    fun `Should be able to create instances concurrently`() {
        val provider = createCryptoServiceProvider()
        assertTrue(provider.isRunning)
        (1..100).createTestCase {
            provider.handleConfigEvent(
                CryptoLibraryConfigImpl(
                    mapOf(
                        "defaultCryptoService" to mapOf(
                            "factoryName" to InMemoryKeyValuePersistenceFactory.NAME
                        ),
                        "publicKeys" to emptyMap()
                    )
                )
            )
            assertNotNull(provider.createCryptoService(CryptoConsts.CryptoCategories.LEDGER))
        }.runAndValidate()
        provider.stop()
        assertFalse(provider.isRunning)
    }

    private fun newAlias(): String = UUID.randomUUID().toString()

    private fun createCryptoServiceProvider(): SoftCryptoServiceProvider {
        val provider = SoftCryptoServiceProvider()
        provider.persistenceFactories = listOf(InMemoryKeyValuePersistenceFactory())
        provider.start()
        provider.handleConfigEvent(
            CryptoLibraryConfigImpl(
                mapOf(
                    "defaultCryptoService" to mapOf(
                        "factoryName" to InMemoryKeyValuePersistenceFactory.NAME
                    ),
                    "publicKeys" to emptyMap()
                )
            )
        )
        return provider
    }

    private fun SoftCryptoServiceProvider.createCryptoService(category: String): CryptoService = getInstance(
        CryptoServiceContext(
            memberId = services.tenantId,
            category = category,
            cipherSuiteFactory = factory,
            config = SoftCryptoServiceConfig(
                passphrase = "PASSPHRASE",
                salt = "SALT"
            )
        )
    )
}

package net.corda.components.crypto.services

import com.typesafe.config.ConfigFactory
import net.corda.crypto.impl.config.CryptoLibraryConfig
import net.corda.crypto.CryptoCategories
import net.corda.crypto.impl.lifecycle.NewCryptoConfigReceived
import net.corda.crypto.testkit.CryptoMocks
import net.corda.crypto.testkit.MockPersistentCacheFactory
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.crypto.SignatureVerificationService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultCryptoServiceProviderTests : LifecycleComponentTestBase() {
    private val masterKeyAlias = "wrapping-key-alias"
    private lateinit var memberId: String
    private lateinit var cryptoMocks: CryptoMocks
    private lateinit var schemeMetadata: CipherSchemeMetadata
    private lateinit var signatureVerifier: SignatureVerificationService

    @BeforeEach
    fun setup() {
        memberId = UUID.randomUUID().toString()
        cryptoMocks = CryptoMocks()
        schemeMetadata = cryptoMocks.schemeMetadata
        signatureVerifier = cryptoMocks.factories.cryptoClients.getSignatureVerificationService()
        setupCoordinator()
    }

    @AfterEach
    fun cleanup() {
        stopCoordinator()
    }

    @Test
    @Timeout(30)
    fun `Should be able to create instance and use it to generate and sign using all supported schemes`() {
        val cryptoService = createCryptoServiceProvider().createCryptoService(CryptoCategories.LEDGER)
        cryptoService.supportedSchemes().forEach { signatureScheme ->
            val testData = UUID.randomUUID().toString().toByteArray()
            val badVerifyData = UUID.randomUUID().toString().toByteArray()
            val alias = newAlias()
            val publicKey = cryptoService.generateKeyPair(alias, signatureScheme)
            val signature = cryptoService.sign(alias, signatureScheme, testData)
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
            val wrappedKeyPair = cryptoService.generateWrappedKeyPair(masterKeyAlias, signatureScheme)
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
                signatureScheme.signatureSpec,
                testData
            )
            assertNotNull(signature)
            assertTrue(signature.isNotEmpty())
            assertTrue(signatureVerifier.isValid(wrappedKeyPair.publicKey, signature, testData))
            assertFalse(signatureVerifier.isValid(wrappedKeyPair.publicKey, signature, badVerifyData))
        }
    }

    private fun newAlias(): String = UUID.randomUUID().toString()

    private fun createCryptoServiceProvider(): DefaultCryptoServiceProvider {
        val provider = DefaultCryptoServiceProvider(
            persistenceFactory = MockPersistentCacheFactory()
        )
        provider.start()
        provider.handleConfigEvent(
            NewCryptoConfigReceived(
                config = CryptoLibraryConfig(
                    ConfigFactory.parseMap(
                        mapOf(
                            "keyCache" to emptyMap<String, String>(),
                            "mngCache" to emptyMap()
                        )
                    )
                )
            )
        )
        return provider
    }

    private fun DefaultCryptoServiceProvider.createCryptoService(category: String): CryptoService = getInstance(
        CryptoServiceContext(
            sandboxId = memberId,
            category = category,
            cipherSuiteFactory = cryptoMocks.factories.cipherSuite,
            config = DefaultCryptoServiceConfig(
                passphrase = "PASSPHRASE",
                salt = "SALT"
            )
        )
    )
}

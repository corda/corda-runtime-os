package net.corda.crypto.impl.dev

import net.corda.crypto.CryptoCategories
import net.corda.crypto.SignatureVerificationServiceInternal
import net.corda.crypto.impl.persistence.DefaultCryptoCachedKeyInfo
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCache
import net.corda.crypto.impl.persistence.SigningKeyCache
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.crypto.testkit.CryptoMocks
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.cipher.suite.schemes.NaSignatureSpec
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.OID_COMPOSITE_KEY_IDENTIFIER
import net.i2p.crypto.eddsa.EdDSAKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.security.PublicKey
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevCryptoServiceTests {
    companion object {
        private const val wrappingKeyAlias = "wrapping-key-alias"
        private val test100ZeroBytes = ByteArray(100)
        private val UNSUPPORTED_SIGNATURE_SCHEME = SignatureScheme(
            codeName = "UNSUPPORTED_SIGNATURE_SCHEME",
            algorithmOIDs = listOf(
                AlgorithmIdentifier(OID_COMPOSITE_KEY_IDENTIFIER)
            ),
            providerName = "SUN",
            algorithmName = CompositeKey.KEY_ALGORITHM,
            algSpec = null,
            keySize = null,
            signatureSpec = NaSignatureSpec
        )

        private lateinit var memberId: String
        private lateinit var cryptoMocks: CryptoMocks
        private lateinit var devCryptoServiceProvider: DevCryptoServiceProvider
        private lateinit var signatureVerifier: SignatureVerificationServiceInternal
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var cryptoServiceCache: DefaultCryptoKeyCache
        private lateinit var signingKeyCache: SigningKeyCache
        private lateinit var cryptoService: DevCryptoService

        @JvmStatic
        @BeforeAll
        fun setup() {
            memberId = UUID.randomUUID().toString()
            cryptoMocks = CryptoMocks()
            schemeMetadata = cryptoMocks.schemeMetadata
            signatureVerifier =
                cryptoMocks.factories.cryptoClients.getSignatureVerificationService() as SignatureVerificationServiceInternal
            devCryptoServiceProvider = DevCryptoServiceProvider()
            cryptoService = devCryptoServiceProvider.getInstance(
                CryptoServiceContext(
                    sandboxId = memberId,
                    category = CryptoCategories.LEDGER,
                    cipherSuiteFactory = cryptoMocks.factories.cipherSuite,
                    config = DevCryptoServiceConfiguration()
                )
            ) as DevCryptoService
            cryptoServiceCache = cryptoService.keyCache
            signingKeyCache = cryptoService.signingCache
            cryptoService.createWrappingKey(wrappingKeyAlias, true)
        }
    }

    @Test
    @Timeout(5)
    fun `Should require wrapping key`() {
        assertTrue(cryptoService.requiresWrappingKey())
    }

    @Test
    @Timeout(5)
    fun `All supported schemes should be defined in cipher suite`() {
        assertTrue(cryptoService.supportedSchemes().isNotEmpty())
        cryptoService.supportedSchemes().forEach {
            assertTrue(schemeMetadata.schemes.contains(it))
        }
    }

    @Test
    @Timeout(5)
    fun `All supported wrapping schemes should be defined in cipher suite`() {
        assertTrue(cryptoService.supportedWrappingSchemes().isNotEmpty())
        cryptoService.supportedWrappingSchemes().forEach {
            assertTrue(schemeMetadata.schemes.contains(it))
        }
    }

    @Test
    @Timeout(5)
    fun `Should support only EDDSA`() {
        assertEquals(1, cryptoService.supportedSchemes().size)
        assertTrue(cryptoService.supportedSchemes().any { it.codeName == EDDSA_ED25519_CODE_NAME })
    }

    @Test
    @Timeout(5)
    fun `Should support only EDDSA for wrapping`() {
        assertEquals(1, cryptoService.supportedWrappingSchemes().size)
        assertTrue(cryptoService.supportedWrappingSchemes().any { it.codeName == EDDSA_ED25519_CODE_NAME })
    }

    @Test
    @Timeout(5)
    fun `containsKey should return true for unknown alias as it generates key when not found`() {
        val alias = newAlias()
        assertTrue(cryptoService.containsKey(alias))
        validateGeneratedKeySpecs(alias, true)
    }

    @Test
    @Timeout(5)
    fun `findPublicKey should return public for unknown alias as it generates key when not found`() {
        val alias = newAlias()
        val publicKey = cryptoService.findPublicKey(alias)
        validateGeneratedKeySpecs(alias, true)
        assertNotNull(publicKey)
        assertEquals(publicKey.algorithm, "EdDSA")
        assertEquals((publicKey as EdDSAKey).params, EdDSANamedCurveTable.getByName("ED25519"))
    }

    @Test
    @Timeout(5)
    fun `Should generate EDDSA key pair with ED25519 curve`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(EDDSA_ED25519_CODE_NAME)
        cryptoService.generateKeyPair(alias, scheme)
        validateGeneratedKeySpecs(alias, false)
    }

    private fun newAlias(): String = UUID.randomUUID().toString()

    private fun validateGeneratedKeySpecs(alias: String, signingCacheShouldExists: Boolean) {
        val keyPair = getGeneratedKeyPair(alias)
        assertNotNull(keyPair)
        assertEquals(memberId, keyPair.memberId)
        assertNotNull(keyPair.privateKey)
        assertNotNull(keyPair.publicKey)
        assertEquals(keyPair.privateKey!!.algorithm, "EdDSA")
        assertEquals((keyPair.privateKey as EdDSAKey).params, EdDSANamedCurveTable.getByName("ED25519"))
        assertEquals(keyPair.publicKey!!.algorithm, "EdDSA")
        assertEquals((keyPair.publicKey as EdDSAKey).params, EdDSANamedCurveTable.getByName("ED25519"))
        val signingKeyInfo = getSigningKeyInfo(keyPair.publicKey!!)
        if (signingCacheShouldExists) {
            assertNotNull(signingKeyInfo)
            assertEquals(alias, signingKeyInfo.alias)
            assertArrayEquals(schemeMetadata.encodeAsByteArray(keyPair.publicKey!!), signingKeyInfo.publicKey)
            assertEquals(memberId, signingKeyInfo.memberId)
            assertNull(signingKeyInfo.externalId)
        } else {
            assertNull(signingKeyInfo)
        }
    }

    private fun getGeneratedKeyPair(alias: String): DefaultCryptoCachedKeyInfo? {
        return cryptoServiceCache.find(alias)
    }

    private fun getSigningKeyInfo(publicKey: PublicKey): SigningPersistentKeyInfo? {
        return signingKeyCache.find(publicKey)
    }
}
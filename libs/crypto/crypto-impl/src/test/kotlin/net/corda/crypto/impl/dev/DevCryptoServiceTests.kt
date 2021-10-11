package net.corda.crypto.impl.dev

import net.corda.crypto.CryptoCategories
import net.corda.crypto.impl.persistence.DefaultCryptoCachedKeyInfo
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCache
import net.corda.crypto.impl.persistence.SigningKeyCache
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.crypto.impl.stubs.CryptoServicesTestFactory
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.cipher.suite.schemes.NaSignatureSpec
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.OID_COMPOSITE_KEY_IDENTIFIER
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.i2p.crypto.eddsa.EdDSAKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.security.KeyPair
import java.security.PublicKey
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevCryptoServiceTests {
    companion object {
        private const val wrappingKeyAlias = "wrapping-key-alias"

        private val EMPTY_CONTEXT = emptyMap<String, String>()

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

        private lateinit var factory: CryptoServicesTestFactory
        private lateinit var devCryptoServiceProvider: DevCryptoServiceProvider
        private lateinit var signatureVerifier: SignatureVerificationService
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var cryptoServiceCache: DefaultCryptoKeyCache
        private lateinit var signingKeyCache: SigningKeyCache
        private lateinit var cryptoService: DevCryptoService

        @JvmStatic
        @BeforeAll
        fun setup() {
            factory = CryptoServicesTestFactory()
            schemeMetadata = factory.schemeMetadata
            signatureVerifier = factory.verifier
            devCryptoServiceProvider = DevCryptoServiceProvider(
                listOf(InMemoryKeyValuePersistenceFactoryProvider())
            )
            cryptoService = devCryptoServiceProvider.getInstance(
                CryptoServiceContext(
                    memberId = factory.memberId,
                    category = CryptoCategories.LEDGER,
                    cipherSuiteFactory = factory,
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
    @Suppress("MaxLineLength")
    fun `containsKey should return true for unknown alias as it generates key when not found`() {
        val testData = UUID.randomUUID().toString().toByteArray()
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val signatureScheme = schemeMetadata.findSignatureScheme(DevCryptoService.SUPPORTED_SCHEME_CODE_NAME)
        val alias = newAlias()
        assertTrue(cryptoService.containsKey(alias))
        val keyPair = validateGeneratedKeySpecs(alias, true)
        val signature = cryptoService.sign(alias, signatureScheme, testData, EMPTY_CONTEXT)
        assertTrue(signatureVerifier.isValid(keyPair.public, signature, testData))
        assertFalse(signatureVerifier.isValid(keyPair.public, signature, badVerifyData))
    }

    @Test
    @Timeout(5)
    @Suppress("MaxLineLength")
    fun `findPublicKey should return public for unknown alias as it generates key when not found and be able to sign and verify with the key`() {
        val testData = UUID.randomUUID().toString().toByteArray()
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val signatureScheme = schemeMetadata.findSignatureScheme(DevCryptoService.SUPPORTED_SCHEME_CODE_NAME)
        val alias = newAlias()
        val publicKey = cryptoService.findPublicKey(alias)
        val keyPair = validateGeneratedKeySpecs(alias, true)
        assertNotNull(publicKey)
        assertEquals(keyPair.public, publicKey)
        val signature = cryptoService.sign(alias, signatureScheme, testData, EMPTY_CONTEXT)
        assertTrue(signatureVerifier.isValid(publicKey, signature, testData))
        assertFalse(signatureVerifier.isValid(publicKey, signature, badVerifyData))
    }

    @Test
    @Timeout(5)
    fun `findPublicKey should return public key for generated key`() {
        val signatureScheme = schemeMetadata.findSignatureScheme(DevCryptoService.SUPPORTED_SCHEME_CODE_NAME)
        val alias = newAlias()
        cryptoService.generateKeyPair(alias, signatureScheme, EMPTY_CONTEXT)
        val generated = validateGeneratedKeySpecs(alias, false)
        val publicKey = cryptoService.findPublicKey(alias)
        assertNotNull(publicKey)
        assertEquals(generated.public, publicKey)
    }

    @Test
    @Timeout(5)
    fun `Should generate EDDSA key pair with ED25519 curve and be able to sign and verify with the key`() {
        val testData = UUID.randomUUID().toString().toByteArray()
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val signatureScheme = schemeMetadata.findSignatureScheme(DevCryptoService.SUPPORTED_SCHEME_CODE_NAME)
        val alias = newAlias()
        val publicKey = cryptoService.generateKeyPair(alias, signatureScheme, EMPTY_CONTEXT)
        val keyPair = validateGeneratedKeySpecs(alias, false)
        assertNotNull(publicKey)
        assertEquals(keyPair.public, publicKey)
        val signature = cryptoService.sign(alias, signatureScheme, testData, EMPTY_CONTEXT)
        assertTrue(signatureVerifier.isValid(publicKey, signature, testData))
        assertFalse(signatureVerifier.isValid(publicKey, signature, badVerifyData))
    }

    @Test
    @Timeout(5)
    fun `Should generate deterministic EDDSA key pair with ED25519 curve based on alias`() {
        val signatureScheme = schemeMetadata.findSignatureScheme(DevCryptoService.SUPPORTED_SCHEME_CODE_NAME)
        val alias1 = newAlias()
        val publicKey1 = cryptoService.generateKeyPair(alias1, signatureScheme, EMPTY_CONTEXT)
        val keyPair1 = validateGeneratedKeySpecs(alias1, false)
        assertNotNull(publicKey1)
        assertEquals(keyPair1.public, publicKey1)
        val publicKey2 = cryptoService.generateKeyPair(alias1, signatureScheme, EMPTY_CONTEXT)
        val keyPair2 = validateGeneratedKeySpecs(alias1, false)
        assertNotNull(publicKey2)
        assertEquals(keyPair2.public, publicKey2)
        assertEquals(keyPair1.public, keyPair2.public)
        assertEquals(keyPair1.private, keyPair2.private)
        val alias3 = newAlias()
        val publicKey3 = cryptoService.generateKeyPair(alias3, signatureScheme, EMPTY_CONTEXT)
        val keyPair3 = validateGeneratedKeySpecs(alias3, false)
        assertNotNull(publicKey3)
        assertEquals(keyPair3.public, publicKey3)
        assertNotEquals(keyPair1.public, keyPair3.public)
        assertNotEquals(keyPair1.private, keyPair3.private)
    }

    @Test
    @Timeout(5)
    fun `Should auto generate key when signing using unknown alias`() {
        val testData = UUID.randomUUID().toString().toByteArray()
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val signatureScheme = schemeMetadata.findSignatureScheme(DevCryptoService.SUPPORTED_SCHEME_CODE_NAME)
        val alias = newAlias()
        val signature = cryptoService.sign(alias, signatureScheme, testData, EMPTY_CONTEXT)
        val keyPair = validateGeneratedKeySpecs(alias, true)
        assertTrue(signatureVerifier.isValid(keyPair.public, signature, testData))
        assertFalse(signatureVerifier.isValid(keyPair.public, signature, badVerifyData))
    }

    private fun newAlias(): String = UUID.randomUUID().toString()

    private fun validateGeneratedKeySpecs(alias: String, signingCacheShouldExists: Boolean): KeyPair {
        val keyPairInfo = getGeneratedKeyPair(alias)
        assertNotNull(keyPairInfo)
        assertEquals(factory.memberId, keyPairInfo.memberId)
        assertNotNull(keyPairInfo.privateKey)
        assertNotNull(keyPairInfo.publicKey)
        assertEquals(keyPairInfo.privateKey!!.algorithm, "EdDSA")
        assertEquals((keyPairInfo.privateKey as EdDSAKey).params, EdDSANamedCurveTable.getByName("ED25519"))
        assertEquals(keyPairInfo.publicKey!!.algorithm, "EdDSA")
        assertEquals((keyPairInfo.publicKey as EdDSAKey).params, EdDSANamedCurveTable.getByName("ED25519"))
        val signingKeyInfo = getSigningKeyInfo(keyPairInfo.publicKey!!)
        if (signingCacheShouldExists) {
            assertNotNull(signingKeyInfo)
            assertEquals(alias, signingKeyInfo.alias)
            assertArrayEquals(schemeMetadata.encodeAsByteArray(keyPairInfo.publicKey!!), signingKeyInfo.publicKey)
            assertEquals(factory.memberId, signingKeyInfo.memberId)
            assertNull(signingKeyInfo.externalId)
        } else {
            assertNull(signingKeyInfo)
        }
        return KeyPair(keyPairInfo.publicKey, keyPairInfo.privateKey)
    }

    @Test
    @Timeout(30)
    fun `Should fail when generating key pair with unsupported signature scheme`() {
        val testData = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        assertFailsWith<CryptoServiceBadRequestException> {
            cryptoService.sign(alias, UNSUPPORTED_SIGNATURE_SCHEME, testData, EMPTY_CONTEXT)
        }
    }

    @Test
    @Timeout(30)
    fun `Should fail when signing with unsupported signature scheme`() {
        val alias = newAlias()
        assertFailsWith<CryptoServiceBadRequestException> {
            cryptoService.generateKeyPair(alias, UNSUPPORTED_SIGNATURE_SCHEME, EMPTY_CONTEXT)
        }
    }

    @Test
    @Timeout(5)
    fun `Should generate wrapped EDDSA key pair with ED25519 curve and be able to sign and verify with the key`() {
        val testData = UUID.randomUUID().toString().toByteArray()
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val signatureScheme = schemeMetadata.findSignatureScheme(DevCryptoService.SUPPORTED_SCHEME_CODE_NAME)
        val wrappedKeyPair = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, signatureScheme, EMPTY_CONTEXT)
        val signature = cryptoService.sign(
            WrappedPrivateKey(
                keyMaterial = wrappedKeyPair.keyMaterial,
                masterKeyAlias = wrappingKeyAlias,
                signatureScheme = signatureScheme,
                encodingVersion = wrappedKeyPair.encodingVersion
            ),
            testData,
            EMPTY_CONTEXT
        )
        assertTrue(signatureVerifier.isValid(wrappedKeyPair.publicKey, signature, testData))
        assertFalse(signatureVerifier.isValid(wrappedKeyPair.publicKey, signature, badVerifyData))
    }

    @Test
    @Timeout(5)
    fun `Should fail signing using wrapped key pair with unknown wrapping key`() {
        val testData = UUID.randomUUID().toString().toByteArray()
        val signatureScheme = schemeMetadata.findSignatureScheme(DevCryptoService.SUPPORTED_SCHEME_CODE_NAME)
        val wrappingKey2Alias = UUID.randomUUID().toString()
        val wrappedKeyPair = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, signatureScheme, EMPTY_CONTEXT)
        assertFailsWith<CryptoServiceBadRequestException> {
            cryptoService.sign(
                WrappedPrivateKey(
                    keyMaterial = wrappedKeyPair.keyMaterial,
                    masterKeyAlias = wrappingKey2Alias,
                    signatureScheme = signatureScheme,
                    encodingVersion = wrappedKeyPair.encodingVersion
                ),
                testData,
                EMPTY_CONTEXT
            )
        }
    }

    private fun getGeneratedKeyPair(alias: String): DefaultCryptoCachedKeyInfo? {
        return cryptoServiceCache.find(alias)
    }

    private fun getSigningKeyInfo(publicKey: PublicKey): SigningPersistentKeyInfo? {
        return signingKeyCache.find(publicKey)
    }
}
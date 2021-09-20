package net.corda.crypto.impl

import net.corda.crypto.impl.persistence.DefaultCryptoKeyCacheImpl
import net.corda.crypto.impl.persistence.SigningKeyCache
import net.corda.crypto.impl.persistence.SigningKeyCacheImpl
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.crypto.FreshKeySigningService
import net.corda.crypto.SignatureVerificationServiceInternal
import net.corda.crypto.SigningService
import net.corda.crypto.testkit.CryptoMocks
import net.corda.v5.base.types.toHexString
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.cipher.suite.schemes.SignatureSpec
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.sha256Bytes
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class FreshKeySigningServiceTests {
    companion object {
        private const val wrappingKeyAlias1 = "test-wrapping-key-alias-1"
        private const val wrappingKeyAlias2 = "test-wrapping-key-alias-2"
        private lateinit var memberId: String
        private lateinit var cryptoMocks: CryptoMocks
        private lateinit var signatureVerifier: SignatureVerificationServiceInternal
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var cryptoServiceCache: DefaultCryptoKeyCacheImpl
        private lateinit var cryptoService: CryptoService
        private lateinit var signingKeyCache: SigningKeyCache

        @BeforeAll
        @JvmStatic
        fun setup() {
            memberId = UUID.randomUUID().toString()
            cryptoMocks = CryptoMocks()
            schemeMetadata = cryptoMocks.schemeMetadata
            signatureVerifier =
                cryptoMocks.factories.cryptoClients
                    .getSignatureVerificationService() as SignatureVerificationServiceInternal
            cryptoServiceCache = DefaultCryptoKeyCacheImpl(
                memberId = memberId,
                passphrase = "PASSPHRASE",
                salt = "SALT",
                schemeMetadata = cryptoMocks.schemeMetadata,
                persistence = cryptoMocks.defaultPersistentKeyCache
            )
            cryptoService = CryptoServiceCircuitBreaker(
                cryptoService = DefaultCryptoService(
                    cache = cryptoServiceCache,
                    schemeMetadata = schemeMetadata,
                    hashingService = cryptoMocks.factories.cryptoClients.getDigestService()
                ),
                timeout = Duration.ofSeconds(5)
            )
            signingKeyCache = SigningKeyCacheImpl(
                memberId = memberId,
                keyEncoder = schemeMetadata,
                persistence = cryptoMocks.signingPersistentKeyCache
            )
        }

        @JvmStatic
        fun supportedSchemes(): Array<SignatureScheme> {
            return cryptoService.supportedSchemes()
        }

        @JvmStatic
        fun signatureSchemesWithPrecalculatedDigest(): List<Arguments> =
            schemeMetadata.digests.flatMap { digest ->
                cryptoService.supportedSchemes().filter { scheme ->
                    scheme.algorithmName == "RSA" || scheme.algorithmName == "EC"
                }.map { scheme ->
                    when (scheme.algorithmName) {
                        "RSA" -> Arguments.of(
                            scheme, SignatureSpec(
                                signatureName = "RSA/NONE/PKCS1Padding",
                                customDigestName = DigestAlgorithmName(digest.algorithmName)
                            )
                        )
                        "EC" -> Arguments.of(
                            scheme, SignatureSpec(
                                signatureName = "NONEwithECDSA",
                                customDigestName = DigestAlgorithmName(digest.algorithmName)
                            )
                        )
                        else -> Arguments.of(
                            scheme, SignatureSpec(
                                signatureName = "NONEwith${scheme.algorithmName}",
                                customDigestName = DigestAlgorithmName(digest.algorithmName)
                            )
                        )
                    }
                }
            }.toList()
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should create fresh key without external id and then sign and verify for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val data = UUID.randomUUID().toString().toByteArray()
        val freshKeyService = createFreshKeyService(signatureScheme, wrappingKeyAlias1)
        val freshKey = freshKeyService.freshKey()
        assertThat(contains(freshKey), `is`(true))
        verifyFreshKeyData(freshKey, null, signatureScheme)
        val signature = freshKeyService.sign(freshKey, data)
        assertEquals(freshKey, signature.by)
        signatureVerifier.verify(freshKey, signature.bytes, data)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should still be able to use fresh key without external id generated and with other master wrapping key for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val data = UUID.randomUUID().toString().toByteArray()
        val freshKeyService1 = createFreshKeyService(signatureScheme, wrappingKeyAlias1)
        val freshKeyService2 = createFreshKeyService(signatureScheme, wrappingKeyAlias2)
        val freshKey1 = freshKeyService1.freshKey()
        assertThat(contains(freshKey1), `is`(true))
        verifyFreshKeyData(freshKey1, null, signatureScheme)
        val freshKey2 = freshKeyService2.freshKey()
        assertThat(contains(freshKey2), `is`(true))
        verifyFreshKeyData(freshKey2, null, signatureScheme)
        // it has to be freshKeyService2 in both cases to emulate wrapping key rotation
        val signature1 = freshKeyService2.sign(freshKey1, data)
        assertEquals(freshKey1, signature1.by)
        signatureVerifier.verify(freshKey1, signature1.bytes, data)
        val signature2 = freshKeyService2.sign(freshKey2, data)
        assertEquals(freshKey2, signature2.by)
        signatureVerifier.verify(freshKey2, signature2.bytes, data)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should fail to use fresh key to sign generated for different member for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val data = UUID.randomUUID().toString().toByteArray()
        val freshKeyService = createFreshKeyService(signatureScheme, wrappingKeyAlias1)
        val freshKey = freshKeyService.freshKey()
        assertThat(contains(freshKey), `is`(true))
        verifyFreshKeyData(freshKey, null, signatureScheme)
        val signature = freshKeyService.sign(freshKey, data)
        assertEquals(freshKey, signature.by)
        signatureVerifier.verify(freshKey, signature.bytes, data)
        val otherMemberFreshKeyService = createFreshKeyServiceWithRandomMemberId(signatureScheme, wrappingKeyAlias1)
        assertFailsWith<CryptoServiceBadRequestException> {
            otherMemberFreshKeyService.sign(freshKey, data)
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should create fresh key with external id and then sign and verify for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val uuid = UUID.randomUUID()
        val data = UUID.randomUUID().toString().toByteArray()
        val freshKeyService = createFreshKeyService(signatureScheme, wrappingKeyAlias1)
        val freshKey = freshKeyService.freshKey(uuid)
        assertThat(contains(freshKey), `is`(true))
        verifyFreshKeyData(freshKey, uuid, signatureScheme)
        val signature = freshKeyService.sign(freshKey, data)
        assertEquals(freshKey, signature.by)
        signatureVerifier.verify(freshKey, signature.bytes, data)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should still be able to use fresh key with external id generated and with other master wrapping key for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        val data = UUID.randomUUID().toString().toByteArray()
        val freshKeyService1 = createFreshKeyService(signatureScheme, wrappingKeyAlias1)
        val freshKeyService2 = createFreshKeyService(signatureScheme, wrappingKeyAlias2)
        val freshKey1 = freshKeyService1.freshKey(uuid1)
        assertThat(contains(freshKey1), `is`(true))
        verifyFreshKeyData(freshKey1, uuid1, signatureScheme)
        val freshKey2 = freshKeyService2.freshKey(uuid2)
        assertThat(contains(freshKey2), `is`(true))
        verifyFreshKeyData(freshKey2, uuid2, signatureScheme)
        // it has to be freshKeyService2 in both cases to emulate wrapping key rotation
        val signature1 = freshKeyService2.sign(freshKey1, data)
        assertEquals(freshKey1, signature1.by)
        signatureVerifier.verify(freshKey1, signature1.bytes, data)
        val signature2 = freshKeyService2.sign(freshKey2, data)
        assertEquals(freshKey2, signature2.by)
        signatureVerifier.verify(freshKey2, signature2.bytes, data)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should be able to use aliased key generated by signing service`(
        signatureScheme: SignatureScheme
    ) {
        val data = UUID.randomUUID().toString().toByteArray()
        val alias = UUID.randomUUID().toString()
        val signingService = createSigningService(signatureScheme)
        val freshKeyService = createFreshKeyService(signatureScheme, wrappingKeyAlias1)
        val publicKey = signingService.generateKeyPair(alias)
        val signature = freshKeyService.sign(publicKey, data)
        assertEquals(publicKey, signature.by)
        signatureVerifier.verify(publicKey, signature.bytes, data)
    }
    @ParameterizedTest
    @MethodSource("signatureSchemesWithPrecalculatedDigest")
    @Timeout(30)
    fun `Should generate keys and then sign using overload with explicit signature spec and verify for all supported schemes`(
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec
    ) {
        val freshKeyService = createFreshKeyService(signatureScheme, wrappingKeyAlias1)
        val freshKey = freshKeyService.freshKey()
        verifyFreshKeyData(freshKey, null, signatureScheme)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = freshKeyService.sign(freshKey, signatureSpec, data)
        assertEquals(freshKey, signature.by)
        signatureVerifier.verify(freshKey, signatureSpec, signature.bytes, data)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should use first known key from CompositeKey when signing using public key overload for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val freshKeyService = createFreshKeyService(signatureScheme, wrappingKeyAlias1)
        val alicePublicKey = generateStandaloneKeyPair(signatureScheme).public
        val bobPublicKey = freshKeyService.freshKey()
        verifyFreshKeyData(bobPublicKey, null, signatureScheme)
        val data = UUID.randomUUID().toString().toByteArray()
        val aliceAndBob = CompositeKey.Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val signature = freshKeyService.sign(aliceAndBob, data)
        assertEquals(bobPublicKey, signature.by)
        signatureVerifier.verify(signature.by, signature.bytes, data)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should fail filtering my keys as it's not implemented yet for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val freshKeyService = createFreshKeyService(signatureScheme, wrappingKeyAlias1)
        val freshKey1 = freshKeyService.freshKey()
        val freshKey2 = freshKeyService.freshKey(UUID.randomUUID())
        assertFailsWith<NotImplementedError> {
            freshKeyService.filterMyKeys(mutableListOf(freshKey1, freshKey2))
        }
    }

    /*
    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should fail filtering my keys as it's not implemented yet for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val freshKeyService = createFreshKeyService(signatureScheme, wrappingKeyAlias1)
        val freshKey1 = freshKeyService.freshKey()
        val freshKey2 = freshKeyService.freshKey(UUID.randomUUID())
        val ourKeys = freshKeyService.filterMyKeys(mutableListOf(freshKey1, freshKey2)).toList()
        assertThat(ourKeys, IsCollectionWithSize.hasSize(2))
        assertThat(ourKeys, hasItem(freshKey1))
        assertThat(ourKeys, hasItem(freshKey2))
    }

     @Test
    @Timeout(30)
    fun `Keys are correctly filtered - none keys belong to us`() {
        val freshKey1 = mockSigningService.generateKeyPair(UUID.randomUUID().toString(), defaultScheme)
        val freshKey2 = keyManagementBackend1.freshKey()
        val ourKeys = keyManagementBackend1.filterMyKeys(mutableListOf(freshKey1, freshKey2)).toList()
        MatcherAssert.assertThat(ourKeys, IsCollectionWithSize.hasSize(1))
        MatcherAssert.assertThat(ourKeys, hasItem(freshKey2))
    }

    @Test
    @Timeout(30)
    fun `Keys are correctly filtered - some of the keys belong to us`() {
        val freshKey1 = mockSigningService.generateKeyPair(UUID.randomUUID().toString(), defaultScheme)
        val freshKey2 = mockSigningService.generateKeyPair(UUID.randomUUID().toString(), defaultScheme)

        val ourKeys = keyManagementBackend1.filterMyKeys(mutableListOf(freshKey1, freshKey2)).toList()
        MatcherAssert.assertThat(ourKeys, `is`(IsEmptyCollection.empty()))
    }
*/

    private fun createSigningService(signatureScheme: SignatureScheme): SigningService =
        SigningServiceImpl(
            cache = signingKeyCache,
            cryptoService = cryptoService,
            defaultSignatureSchemeCodeName = signatureScheme.codeName,
            schemeMetadata = schemeMetadata
        )

    private fun createFreshKeyService(
        signatureScheme: SignatureScheme,
        wrappingKeyAlias: String
    ): FreshKeySigningService =
        FreshKeySigningServiceImpl(
            cache = signingKeyCache,
            cryptoService = cryptoService,
            freshKeysCryptoService = cryptoService,
            schemeMetadata = schemeMetadata,
            defaultFreshKeySignatureSchemeCodeName = signatureScheme.codeName,
            masterWrappingKeyAlias = wrappingKeyAlias
        ).also { it.ensureWrappingKey() }

    private fun createFreshKeyServiceWithRandomMemberId(
        signatureScheme: SignatureScheme,
        wrappingKeyAlias: String
    ): FreshKeySigningService {
        val cache = SigningKeyCacheImpl(
            memberId = UUID.randomUUID().toString(),
            keyEncoder = schemeMetadata,
            persistence = cryptoMocks.signingPersistentKeyCache
        )
        return FreshKeySigningServiceImpl(
            cache = cache,
            cryptoService = cryptoService,
            freshKeysCryptoService = cryptoService,
            schemeMetadata = schemeMetadata,
            defaultFreshKeySignatureSchemeCodeName = signatureScheme.codeName,
            masterWrappingKeyAlias = wrappingKeyAlias
        ).also { it.ensureWrappingKey() }
    }

    private fun get(publicKey: PublicKey): SigningPersistentKeyInfo? {
        return cryptoMocks.signingPersistentKeyCache.data.values.firstOrNull {
            it.first.publicKeyHash == "$memberId:${publicKey.sha256Bytes().toHexString()}"
        }?.first
    }

    private fun contains(publicKey: PublicKey): Boolean = get(publicKey) != null

    private fun verifyFreshKeyData(freshKey: PublicKey, uuid: UUID?, signatureScheme: SignatureScheme) {
        val freshKeyData = get(freshKey)
        assertNotNull(freshKeyData)
        assertThat(freshKeyData.memberId, `is`(memberId))
        assertThat(freshKeyData.publicKeyHash, `is`("$memberId:${freshKey.sha256Bytes().toHexString()}"))
        if (uuid != null)
            assertThat(freshKeyData.externalId, `is`(uuid))
        else
            assertThat(freshKeyData.externalId, IsNull())
        assertThat(freshKeyData.publicKey, `is`(freshKey.encoded))
        assertThat(freshKeyData.alias, IsNull())
        assertThat(freshKeyData.privateKeyMaterial, `is`(notNullValue()))
        assertThat(freshKeyData.schemeCodeName, `is`(signatureScheme.codeName))
        assertThat(freshKeyData.version, `is`(1))
    }

    private fun generateStandaloneKeyPair(signatureScheme: SignatureScheme): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            signatureScheme.algorithmName,
            schemeMetadata.providers[signatureScheme.providerName]
        )
        if (signatureScheme.algSpec != null) {
            keyPairGenerator.initialize(signatureScheme.algSpec )
        } else if (signatureScheme.keySize != null) {
            keyPairGenerator.initialize(signatureScheme.keySize!!)
        }
        return keyPairGenerator.generateKeyPair()
    }
}

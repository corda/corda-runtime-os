package net.corda.crypto.impl

import net.corda.crypto.impl.persistence.DefaultCryptoKeyCacheImpl
import net.corda.crypto.impl.persistence.SigningKeyCache
import net.corda.crypto.impl.persistence.SigningKeyCacheImpl
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.crypto.FreshKeySigningService
import net.corda.crypto.SigningService
import net.corda.crypto.testkit.CryptoMocks
import net.corda.v5.base.types.toHexString
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.sha256Bytes
import org.hamcrest.CoreMatchers.`is`
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
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SigningServiceImplTests {
    companion object {
        private const val wrappingKeyAlias = "test-wrapping-key-alias-42"
        private lateinit var memberId: String
        private lateinit var cryptoMocks: CryptoMocks
        private lateinit var signatureVerifier: SignatureVerificationService
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var cryptoServiceCache: DefaultCryptoKeyCacheImpl
        private lateinit var cryptoService: CryptoService
        private lateinit var signingKeyCache: SigningKeyCache

        @JvmStatic
        @BeforeAll
        fun setup() {
            memberId = UUID.randomUUID().toString()
            cryptoMocks = CryptoMocks()
            schemeMetadata = cryptoMocks.schemeMetadata
            signatureVerifier =
                cryptoMocks.factories.cryptoClients.getSignatureVerificationService()
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
            cryptoService.createWrappingKey(wrappingKeyAlias, true)
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
    @Suppress("MaxLineLength")
    fun `Should generate keys and then sign using public key overload and verify multiple times for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val signingService = createSigningService(signatureScheme)
        for (i in 0..5) {
            val alias = UUID.randomUUID().toString()
            val publicKey = signingService.generateKeyPair(alias)
            validateGeneratedKeyData(publicKey, alias, signatureScheme)
            val data = UUID.randomUUID().toString().toByteArray()
            val signature = signingService.sign(publicKey, data)
            assertEquals(publicKey, signature.by)
            signatureVerifier.verify(publicKey, signature.bytes, data)
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should fail to use key for signing with public key overload generated for different member for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val data = UUID.randomUUID().toString().toByteArray()
        val signingService = createSigningService(signatureScheme)
        val alias = UUID.randomUUID().toString()
        val publicKey = signingService.generateKeyPair(alias)
        validateGeneratedKeyData(publicKey, alias, signatureScheme)
        val signature = signingService.sign(publicKey, data)
        assertEquals(publicKey, signature.by)
        signatureVerifier.verify(publicKey, signature.bytes, data)
        val otherMemberSigningService = createSigningServiceWithRandomMemberId(signatureScheme)
        assertFailsWith<CryptoServiceBadRequestException> {
            otherMemberSigningService.sign(publicKey, data)
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should generate keys and then sign using alias overload and verify multiple times for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val signingService = createSigningService(signatureScheme)
        for (i in 0..5) {
            val alias = UUID.randomUUID().toString()
            val publicKey = signingService.generateKeyPair(alias)
            validateGeneratedKeyData(publicKey, alias, signatureScheme)
            val data = UUID.randomUUID().toString().toByteArray()
            val signature = signingService.sign(alias, data)
            signatureVerifier.verify(publicKey, signature, data)
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should fail to use key for signing with alias overload generated for different member for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val data = UUID.randomUUID().toString().toByteArray()
        val signingService = createSigningService(signatureScheme)
        val alias = UUID.randomUUID().toString()
        val publicKey = signingService.generateKeyPair(alias)
        validateGeneratedKeyData(publicKey, alias, signatureScheme)
        val signature = signingService.sign(alias, data)
        signatureVerifier.verify(publicKey, signature, data)
        val otherMemberSigningService = createSigningServiceWithRandomMemberId(signatureScheme)
        assertFailsWith<CryptoServiceBadRequestException> {
            otherMemberSigningService.sign(alias, data)
        }
    }

    @ParameterizedTest
    @MethodSource("signatureSchemesWithPrecalculatedDigest")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should generate keys and then sign using public key overload with explicit signature spec and verify for all supported schemes`(
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec
    ) {
        val signingService = createSigningService(signatureScheme)
        val alias = UUID.randomUUID().toString()
        val publicKey = signingService.generateKeyPair(alias)
        validateGeneratedKeyData(publicKey, alias, signatureScheme)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = signingService.sign(publicKey, signatureSpec, data)
        assertEquals(publicKey, signature.by)
        signatureVerifier.verify(publicKey, signatureSpec, signature.bytes, data)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should be able to use wrapped key for signing for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val signingService = createSigningService(signatureScheme)
        val freshKeySigningService = createFreshKeyService(signatureScheme)
        val publicKey = freshKeySigningService.freshKey()
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = signingService.sign(publicKey, data)
        signatureVerifier.verify(publicKey, signature.bytes, data)
    }

    @ParameterizedTest
    @MethodSource("signatureSchemesWithPrecalculatedDigest")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should generate keys and then sign using alias overload with explicit signature spec and verify for all supported schemes`(
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec
    ) {
        val signingService = createSigningService(signatureScheme)
        val alias = UUID.randomUUID().toString()
        val publicKey = signingService.generateKeyPair(alias)
        validateGeneratedKeyData(publicKey, alias, signatureScheme)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = signingService.sign(alias, signatureSpec, data)
        signatureVerifier.verify(publicKey, signatureSpec, signature, data)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should find generated public key works correctly for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val signingService = createSigningService(signatureScheme)
        val alias1 = UUID.randomUUID().toString()
        val alias2 = UUID.randomUUID().toString()
        val generatedPublicKey1 = signingService.generateKeyPair(alias1)
        val generatedPublicKey2 = signingService.generateKeyPair(alias2)
        val returnedPublicKey1 = signingService.findPublicKey(alias1)
        val returnedPublicKey2 = signingService.findPublicKey(alias2)
        assertThat(returnedPublicKey1, `is`(generatedPublicKey1))
        assertThat(returnedPublicKey2, `is`(generatedPublicKey2))
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should not find public key when key pair hasn't been generated yet for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val signingService = createSigningService(signatureScheme)
        val alias1 = UUID.randomUUID().toString()
        val alias2 = UUID.randomUUID().toString()
        signingService.generateKeyPair(alias1)
        val publicKey = signingService.findPublicKey(alias2)
        assertThat(publicKey, IsNull())
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should use first known key from CompositeKey when signing using public key overload for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val signingService = createSigningService(signatureScheme)
        val alias = UUID.randomUUID().toString()
        val alicePublicKey = generateStandaloneKeyPair(signatureScheme).public
        val bobPublicKey = signingService.generateKeyPair(alias)
        validateGeneratedKeyData(bobPublicKey, alias, signatureScheme)
        val data = UUID.randomUUID().toString().toByteArray()
        val aliceAndBob = CompositeKey.Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val signature = signingService.sign(aliceAndBob, data)
        assertEquals(bobPublicKey, signature.by)
        signatureVerifier.verify(signature.by, signature.bytes, data)
    }

    private fun createSigningService(signatureScheme: SignatureScheme): SigningService =
        SigningServiceImpl(
            cache = signingKeyCache,
            cryptoService = cryptoService,
            defaultSignatureSchemeCodeName = signatureScheme.codeName,
            schemeMetadata = schemeMetadata
        )

    private fun createFreshKeyService(signatureScheme: SignatureScheme): FreshKeySigningService =
        FreshKeySigningServiceImpl(
            cache = signingKeyCache,
            cryptoService = cryptoService,
            freshKeysCryptoService = cryptoService,
            schemeMetadata = schemeMetadata,
            defaultFreshKeySignatureSchemeCodeName = signatureScheme.codeName,
            masterWrappingKeyAlias = wrappingKeyAlias
        )

    private fun createSigningServiceWithRandomMemberId(signatureScheme: SignatureScheme): SigningService {
        val cache = SigningKeyCacheImpl(
            memberId = UUID.randomUUID().toString(),
            keyEncoder = schemeMetadata,
            persistence = cryptoMocks.signingPersistentKeyCache
        )
        return SigningServiceImpl(
            cache = cache,
            cryptoService = cryptoService,
            defaultSignatureSchemeCodeName = signatureScheme.codeName,
            schemeMetadata = schemeMetadata
        )
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

    private fun get(publicKey: PublicKey): SigningPersistentKeyInfo {
        return cryptoMocks.signingPersistentKeyCache.data.values.first {
            it.first.publicKeyHash == "$memberId:${publicKey.sha256Bytes().toHexString()}"
        }.first
    }

    private fun validateGeneratedKeyData(
        generatedPublicKey: PublicKey,
        alias: String,
        signatureScheme: SignatureScheme
    ) {
        val generatedKeyData = get(generatedPublicKey)
        assertNotNull(generatedKeyData)
        assertThat(generatedKeyData.memberId, `is`(memberId))
        assertThat(generatedKeyData.publicKeyHash, `is`(
            "$memberId:${generatedPublicKey.sha256Bytes().toHexString()}")
        )
        assertThat(generatedKeyData.externalId, IsNull())
        assertThat(generatedKeyData.publicKey, `is`(generatedPublicKey.encoded))
        assertThat(generatedKeyData.alias, `is`("$memberId:$alias"))
        assertThat(generatedKeyData.privateKeyMaterial, IsNull())
        assertThat(generatedKeyData.schemeCodeName, `is`(signatureScheme.codeName))
        assertThat(generatedKeyData.version, `is`(1))
    }
}

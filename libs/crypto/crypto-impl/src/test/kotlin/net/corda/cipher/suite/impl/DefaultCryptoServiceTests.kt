package net.corda.cipher.suite.impl

import net.corda.crypto.testkit.CryptoMocks
import net.corda.crypto.impl.caching.SimplePersistentCacheFactory
import net.corda.crypto.impl.caching.SimplePersistentCacheImpl
import net.corda.test.MockDatabaseBuilder
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureVerificationService
import org.hibernate.SessionFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultCryptoServiceTests {
    companion object {
        private const val wrappingKeyAlias = "wrapping-key-alias"
        private val algoAndSchemeAlgoPairs = listOf(
                Pair("NONEwithRSA", "RSA"),
                Pair("SHA224withRSA", "RSA"),
                Pair("SHA256withRSA", "RSA"),
                Pair("SHA384withRSA", "RSA"),
                Pair("SHA512withRSA", "RSA"),
                Pair("NONEwithECDSA", "EC"),
                Pair("SHA1withECDSA", "EC"),
                Pair("SHA224withECDSA", "EC"),
                Pair("SHA256withECDSA", "EC"),
                Pair("SHA384withECDSA", "EC"),
                Pair("SHA512withECDSA", "EC"),
                Pair("NONEwithEdDSA", "EdDSA"),
        )

        lateinit var sessionFactory: SessionFactory
        lateinit var basicKeyCache: DefaultKeyCacheImpl
        lateinit var signatureVerifier: SignatureVerificationService
        lateinit var schemeMetadata: CipherSchemeMetadata
        lateinit var cryptoService: CryptoService

        @JvmStatic
        fun supportedSchemes(): Array<SignatureScheme> {
            return cryptoService.supportedSchemes()
        }

        @JvmStatic
        fun supportedWrappingSchemes(): Array<SignatureScheme> {
            return cryptoService.supportedWrappingSchemes()
        }

        @JvmStatic
        fun customSignatureSchemes(): List<Arguments> =
                schemeMetadata.digests.flatMap { digest ->
                    cryptoService.supportedSchemes().filter { scheme ->
                        scheme.algorithmName == "RSA" || scheme.algorithmName == "EC"
                    }.map { scheme ->
                        when (scheme.algorithmName) {
                            "RSA" -> Arguments.of(scheme, SignatureSpec(
                                    signatureName = "RSA/NONE/PKCS1Padding",
                                    customDigestName = DigestAlgorithmName(digest.algorithmName)
                            ))
                            "EC" -> Arguments.of(scheme, SignatureSpec(
                                    signatureName = "NONEwithECDSA",
                                    customDigestName = DigestAlgorithmName(digest.algorithmName)
                            ))
                            else -> Arguments.of(scheme, SignatureSpec(
                                    signatureName = "NONEwith${scheme.algorithmName}",
                                    customDigestName = DigestAlgorithmName(digest.algorithmName)
                            ))
                        }
                    }
                }.toList()

        @JvmStatic
        fun schemeAndAlgos(): List<Arguments> {
            return cryptoService.supportedSchemes().flatMap { scheme: SignatureScheme ->
                // Run only for the schemeAlgoName passed in. EC has multiple schemes for different curves,
                // so run for all schemes that this algorithm applies to
                val actualSchemeAlgo = if (scheme.algorithmName == "1.3.101.112") {
                    "EdDSA"
                } else {
                    scheme.algorithmName
                } // Allow ED curve to match
                algoAndSchemeAlgoPairs.mapNotNull { pair ->
                    val (algo, schemeAlgo) = pair
                    if (schemeAlgo == actualSchemeAlgo) Arguments.of(scheme, algo) else null
                }
            }
        }

        @JvmStatic
        @BeforeAll
        fun setup() {
            schemeMetadata = CipherSchemeMetadataProviderImpl().getInstance()
            val cryptoMocks = CryptoMocks(schemeMetadataImpl = schemeMetadata)
            signatureVerifier = cryptoMocks.signatureVerificationService()
            sessionFactory = MockDatabaseBuilder.resetDatabase()
            val cache = SimplePersistentCacheImpl<DefaultCachedKey, DefaultCryptoPersistentKey>(
                    DefaultCryptoPersistentKey::class.java,
                    sessionFactory
            )
            basicKeyCache = DefaultKeyCacheImpl(
                    sandboxId = "BasicCryptoServiceTests-s111",
                    partition = "BasicCryptoServiceTests-p111",
                    passphrase = "passphrase-111",
                    salt = "11",
                    cacheFactory = object : SimplePersistentCacheFactory<DefaultCachedKey, DefaultCryptoPersistentKey> {
                        override fun create() = cache
                    },
                    schemeMetadata = schemeMetadata
            )
            cryptoService = CryptoServiceCircuitBreaker(
                    cryptoService = DefaultCryptoService(
                            cache = basicKeyCache,
                            schemeMetadata = schemeMetadata,
                            hashingService = cryptoMocks.digestService()
                    ),
                    timeout = Duration.ofSeconds(5)
            )
            cryptoService.createWrappingKey(wrappingKeyAlias, true)
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            sessionFactory.close()
        }
    }

    @Test
    @Timeout(30)
    fun `Should require wrapping key`() {
        assertTrue(cryptoService.requiresWrappingKey())
    }

    @Test
    @Timeout(30)
    fun `All supported schemes should be defined in cipher suite`() {
        assertTrue(cryptoService.supportedSchemes().isNotEmpty())
        cryptoService.supportedSchemes().forEach {
            assertTrue(schemeMetadata.schemes.contains(it))
        }
    }

    @Test
    @Timeout(30)
    fun `All supported wraping schemes should be defined in cipher suite`() {
        assertTrue(cryptoService.supportedWrappingSchemes().isNotEmpty())
        cryptoService.supportedWrappingSchemes().forEach {
            assertTrue(schemeMetadata.schemes.contains(it))
        }
    }

    @Test
    @Timeout(30)
    fun `containsKey should return false for unknown alias`() {
        val alias = newAlias()
        assertFalse(cryptoService.containsKey(alias))
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `containsKey should return true for known alias`(scheme: SignatureScheme) {
        val alias = newAlias()
        cryptoService.generateKeyPair(alias, scheme)
        assertTrue(cryptoService.containsKey(alias))
    }

    @Test
    @Timeout(30)
    fun `findPublicKey should return null for unknown alias`() {
        val alias = newAlias()
        assertNull(cryptoService.findPublicKey(alias))
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `findPublicKey should return public key for known alias`(scheme: SignatureScheme) {
        val alias = newAlias()
        cryptoService.generateKeyPair(alias, scheme)
        val publicKey = cryptoService.findPublicKey(alias)
        assertNotNull(publicKey)
        val expectedAlgo = if (scheme.algorithmName == "1.3.101.112") {
            "EdDSA"
        } else {
            scheme.algorithmName
        }
        if (scheme.algorithmName == "SPHINCS256") {
            assertEquals("SPHINCS-256", publicKey.algorithm)
        } else {
            assertEquals(expectedAlgo, publicKey.algorithm)
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should generate key pair with all supported schemes and be able to sign and verify using default signature scheme`(signatureScheme: SignatureScheme) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        val publicKey = cryptoService.generateKeyPair(alias, signatureScheme)
        assertNotNull(publicKey)
        val expectedAlgo = if (signatureScheme.algorithmName == "1.3.101.112") {
            "EdDSA"
        } else {
            signatureScheme.algorithmName
        }
        if (signatureScheme.algorithmName == "SPHINCS256") {
            assertEquals("SPHINCS-256", publicKey.algorithm)
        } else {
            assertEquals(expectedAlgo, publicKey.algorithm)
        }
        assertTrue(getAllRows().map { it.alias }.any { it.endsWith(":$alias") })
        val signature = cryptoService.sign(alias, signatureScheme, testData)
        assertTrue(signatureVerifier.isValid(publicKey, signature, testData))
        assertFalse(signatureVerifier.isValid(publicKey, signature, badVerifyData))
    }

    @ParameterizedTest
    @MethodSource("customSignatureSchemes")
    @Timeout(300)
    @Suppress("MaxLineLength")
    fun `Should generate key pair with all supported schemes and be able to sign and verify using custom signature scheme`(
            signatureScheme: SignatureScheme,
            signatureSpec: SignatureSpec
    ) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        val publicKey = cryptoService.generateKeyPair(alias, signatureScheme)
        assertNotNull(publicKey)
        val expectedAlgo = if (signatureScheme.algorithmName == "1.3.101.112") {
            "EdDSA"
        } else {
            signatureScheme.algorithmName
        }
        if (signatureScheme.algorithmName == "SPHINCS256") {
            assertEquals("SPHINCS-256", publicKey.algorithm)
        } else {
            assertEquals(expectedAlgo, publicKey.algorithm)
        }
        assertTrue(getAllRows().map { it.alias }.any { it.endsWith(":$alias") })
        val signature = cryptoService.sign(alias, signatureScheme, signatureSpec, testData)
        assertTrue(signatureSpec.precalculateHash)
        assertTrue(signatureVerifier.isValid(publicKey, signatureSpec, signature, testData))
        assertFalse(signatureVerifier.isValid(publicKey, signatureSpec, signature, badVerifyData))
    }

    private fun newAlias(): String = UUID.randomUUID().toString()

    private fun getAllRows(): List<DefaultCryptoPersistentKey> {
        return sessionFactory.openSession().use { session ->
            val cb = session.criteriaBuilder
            val cr = cb.createQuery(DefaultCryptoPersistentKey::class.java)
            val root = cr.from(DefaultCryptoPersistentKey::class.java)
            cr.select(root)
            val query = session.createQuery(cr)
            query.resultList
        }
    }
}

package net.corda.crypto.tck.impl.compliance

import net.corda.crypto.cipher.suite.CRYPTO_CATEGORY
import net.corda.crypto.cipher.suite.CRYPTO_KEY_TYPE
import net.corda.crypto.cipher.suite.CRYPTO_KEY_TYPE_KEYPAIR
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.GeneratedKey
import net.corda.crypto.cipher.suite.GeneratedPublicKey
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.SharedSecretAliasSpec
import net.corda.crypto.cipher.suite.SharedSecretWrappedSpec
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.EDDSA_ED25519_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.crypto.cipher.suite.schemes.KeySchemeTemplate
import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.DefaultSignatureOIDMap
import net.corda.crypto.hes.HybridEncryptionParams
import net.corda.crypto.hes.core.impl.decryptWithStableKeyPair
import net.corda.crypto.hes.core.impl.encryptWithEphemeralKeyPair
import net.corda.crypto.impl.decorators.requiresWrappingKey
import net.corda.crypto.impl.decorators.supportsKeyDelete
import net.corda.crypto.impl.decorators.supportsSharedSecretDerivation
import net.corda.crypto.tck.impl.ComplianceSpec
import net.corda.crypto.tck.impl.ComplianceSpecExtension
import net.corda.crypto.tck.impl.ConcurrentTests.Companion.createTestCase
import net.corda.crypto.tck.impl.CryptoServiceProviderMap
import net.corda.v5.crypto.COMPOSITE_KEY_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.OID_COMPOSITE_KEY
import net.corda.v5.crypto.SignatureSpec
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.jcajce.provider.util.DigestFactory
import org.bouncycastle.operator.ContentSigner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.ConcurrentLinkedQueue

val OID_COMPOSITE_KEY_IDENTIFIER = ASN1ObjectIdentifier(OID_COMPOSITE_KEY)

// we are only going to use in a negative sense
val FAKE_COMPOSITE_KEY_TEMPLATE = KeySchemeTemplate(
    codeName = COMPOSITE_KEY_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(OID_COMPOSITE_KEY_IDENTIFIER)),
    algorithmName = "COMPOSITE",
    algSpec = null,
    keySize = null,
    capabilities = setOf(KeySchemeCapability.SIGN)
)

@ExtendWith(ServiceExtension::class, ComplianceSpecExtension::class)
@Suppress("TooManyFunctions")
class CryptoServiceCompliance : AbstractCompliance() {
    companion object {
        @InjectService(timeout = 10000L)
        lateinit var providers: CryptoServiceProviderMap

        @InjectService(timeout = 10000L)
        lateinit var schemeMetadata: CipherSchemeMetadata

        @InjectService(timeout = 10000L)
        lateinit var verifier: SignatureVerificationService
    }

    private val schemesAndSignatureSpecs = mutableListOf<Pair<String, SignatureSpec>>()

    private lateinit var derivingSchemes: List<KeyScheme>

    @BeforeEach
    fun setup(spec: ComplianceSpec) {
        super.setup(spec, providers)
        service.supportedSchemes.filter {
            it.key.canDo(KeySchemeCapability.SIGN)
        }.forEach {
            it.value.forEach { s -> schemesAndSignatureSpecs.add(Pair(it.key.codeName, s)) }
        }
        derivingSchemes = service.supportedSchemes.keys.filter {
            it.canDo(KeySchemeCapability.SHARED_SECRET_DERIVATION)
        }
    }

    @AfterEach
    fun cleanup() {
        if (masterKeyAlias != null) {
            deleteWrappingKey(masterKeyAlias!!)
        }
    }

    @Test
    fun `Should support at least one signing key schema supported by scheme matadata`() {
        logger.info(
            "Supported key schemes by service: [${service.supportedSchemes.keys.joinToString()}]"
        )
        val supported = service.supportedSchemes.filter {
            it.key.canDo(KeySchemeCapability.SIGN) && schemeMetadata.schemes.contains(it.key)
        }
        logger.info("Supported by service and scheme metadata: [${supported.keys.joinToString()}]")
        assertTrue(supported.isNotEmpty(), "The service must support at least one key scheme.")
        val withoutSignatureSpec = supported.filter { it.value.isEmpty() }.map { it.key.toString() }
        assertTrue(
            withoutSignatureSpec.isEmpty(),
            "There must be at least one signatures spec for [${withoutSignatureSpec.joinToString()}]"
        )
    }

    @Test
    fun `Should support at least one deriving key schema supported by scheme matadata`() {
        assumeTrue(
            service.supportsSharedSecretDerivation,
            "The test expects that service supports secret derivation."
        )
        logger.info(
            "Supported key schemes by service: [${service.supportedSchemes.keys.joinToString()}]"
        )
        val supported = service.supportedSchemes.filter {
            it.key.canDo(KeySchemeCapability.SHARED_SECRET_DERIVATION) && schemeMetadata.schemes.contains(it.key)
        }
        logger.info("Supported by service and scheme metadata: [${supported.keys.joinToString()}]")
        assertTrue(supported.isNotEmpty(), "The service must support at least one key scheme.")
    }

    @Test
    fun `delete should throw UnsupportedOperationException if it's not supported but still called`() {
        assumeFalse(
            service.supportsKeyDelete,
            "The test expects that service doesn't support key deletion."
        )
        assertThrows(UnsupportedOperationException::class.java) {
            service.delete(
                "alias", mapOf(
                    CRYPTO_TENANT_ID to tenantId,
                    CRYPTO_KEY_TYPE to CRYPTO_KEY_TYPE_KEYPAIR
                )
            )
        }
    }

    @Test
    fun `createWrappingKey should throw UnsupportedOperationException if it's not supported but still called`() {
        assumeFalse(
            service.requiresWrappingKey,
            "The test expects that service doesn't require wrapping keys."
        )
        assertThrows(UnsupportedOperationException::class.java) {
            service.createWrappingKey(
                "masterKeyAlias", false, mapOf(
                    CRYPTO_TENANT_ID to CryptoTenants.CRYPTO
                )
            )
        }
    }

    @Test
    fun `deriveSharedSecret should throw UnsupportedOperationException if it's not supported but still called`() {
        assumeFalse(
            service.supportsSharedSecretDerivation,
            "The test expects that service doesn't support secret derivation."
        )
        assertThrows(UnsupportedOperationException::class.java) {
            service.deriveSharedSecret(SharedSecretAliasSpec(
                publicKey = object : PublicKey {
                    override fun getAlgorithm(): String = "EC"
                    override fun getFormat(): String = ASN1Encoding.DER
                    override fun getEncoded(): ByteArray = "key".toByteArray()
                },
                hsmAlias = "alias",
                keyScheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC"),
                otherPublicKey = object : PublicKey {
                    override fun getAlgorithm(): String = "EC"
                    override fun getFormat(): String = ASN1Encoding.DER
                    override fun getEncoded(): ByteArray = "key".toByteArray()
                }
            ), mapOf(
                CRYPTO_TENANT_ID to tenantId
            ))
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should be able to generate key pair with proposed alias then endcode & decode public key and then sign and verify`() {
        schemesAndSignatureSpecs.forEach { (codeName, signatureSpec) ->
            logger.info("scheme=$codeName, signatureSpec=$signatureSpec")
            val keyScheme = schemeMetadata.findKeyScheme(codeName)
            val generatedKeys = ConcurrentLinkedQueue<PublicKey>()
            val experiments = ConcurrentLinkedQueue<SigningExperiment>()
            try {
                (0 until compliance.options.concurrency).createTestCase {
                    val alias = compliance.generateRandomIdentifier()
                    val key = `Should generate key with expected key scheme`(alias, masterKeyAlias, keyScheme)
                    generatedKeys.add(key.publicKey)
                    experiments.addAll(
                        `Should be able to sign byte arrays of different lengths`(key, keyScheme, signatureSpec)
                    )
                }.runAndValidate()
            } catch (e: Throwable) {
                experiments.cleanupSigningKeyPairs()
                throw e
            }
            experiments.validateAndCleanupKeys(generatedKeys)
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should be able to generate key pair without proposed alias, suggesting wrapped key, then encode & decode public key and then sign and verify`() {
        schemesAndSignatureSpecs.forEach { (codeName, signatureSpec) ->
            logger.info("scheme=$codeName, signatureSpec=$signatureSpec")
            val keyScheme = schemeMetadata.findKeyScheme(codeName)
            val generatedKeys = ConcurrentLinkedQueue<PublicKey>()
            val experiments = ConcurrentLinkedQueue<SigningExperiment>()
            try {
                (0 until compliance.options.concurrency).createTestCase {
                    val key = `Should generate key with expected key scheme`(null, masterKeyAlias, keyScheme)
                    generatedKeys.add(key.publicKey)
                    experiments.addAll(
                        `Should be able to sign byte arrays of different lengths`(key, keyScheme, signatureSpec)
                    )
                }.runAndValidate()
            } catch (e: Throwable) {
                experiments.cleanupSigningKeyPairs()
                throw e
            }
            experiments.validateAndCleanupKeys(generatedKeys)
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should be able to generate key pair with proposed alias then endcode & decode public key and then derive shared secret`() {
        assumeTrue(
            service.supportsSharedSecretDerivation,
            "The test expects that service supports secret derivation."
        )
        derivingSchemes.forEach { keyScheme ->
            logger.info("scheme=$keyScheme")
            val generatedKeys = ConcurrentLinkedQueue<GeneratedKey>()
            try {
                (0 until compliance.options.concurrency).createTestCase {
                    val alias = compliance.generateRandomIdentifier()
                    val key = `Should generate key with expected key scheme`(alias, masterKeyAlias, keyScheme)
                    generatedKeys.add(key)
                    `Should be able to derive shared secret and use it for encryption`(key, keyScheme)
                }.runAndValidate()
            } catch (e: Throwable) {
                generatedKeys.cleanupKeyPairs()
                throw e
            }
            generatedKeys.encodeDecodeAndCleanupKeys()
        }
    }

    @Test
    fun `Should throw IllegalStateException when wrapping key is required and already exists`() {
        assumeTrue(!masterKeyAlias.isNullOrBlank())
        assertThrows<IllegalStateException> {
            service.createWrappingKey(
                masterKeyAlias!!, true, mapOf(
                    CRYPTO_TENANT_ID to CryptoTenants.CRYPTO
                )
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when key scheme is not supported`() {
        assertThrows<IllegalArgumentException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    keyScheme = FAKE_COMPOSITE_KEY_TEMPLATE.makeScheme("BC"),
                    alias = compliance.generateRandomIdentifier(),
                    masterKeyAlias = masterKeyAlias
                ),
                context = mapOf(
                    CRYPTO_TENANT_ID to tenantId,
                    CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                )
            )
        }
        assertThrows<IllegalArgumentException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    keyScheme = FAKE_COMPOSITE_KEY_TEMPLATE.makeScheme("BC"),
                    alias = null,
                    masterKeyAlias = masterKeyAlias
                ),
                context = mapOf(
                    CRYPTO_TENANT_ID to tenantId,
                    CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                )
            )
        }
    }

    private fun `Should be able to derive shared secret and use it for encryption`(
        key: GeneratedKey,
        keyScheme: KeyScheme
    ) {
        val plainText = "Hello MGM!".toByteArray()
        val encryptedData = encryptWithEphemeralKeyPair(
            schemeMetadata = schemeMetadata,
            otherPublicKey = key.publicKey,
            plainText = plainText
        ) { _, _ ->
            HybridEncryptionParams(ByteArray(DigestFactory.getDigest("SHA-256").digestSize).apply {
                schemeMetadata.secureRandom.nextBytes(this)
            }, null)
        }
        val spec = if (key is GeneratedWrappedKey) {
            SharedSecretWrappedSpec(
                publicKey = key.publicKey,
                keyMaterialSpec = KeyMaterialSpec(
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = masterKeyAlias,
                    encodingVersion = key.encodingVersion
                ),
                keyScheme = keyScheme,
                otherPublicKey = encryptedData.publicKey
            )
        } else {
            key as GeneratedPublicKey
            val spec = SharedSecretAliasSpec(
                publicKey = key.publicKey,
                hsmAlias = key.hsmAlias,
                keyScheme = keyScheme,
                otherPublicKey = encryptedData.publicKey
            )
            assertThrows<IllegalArgumentException>(
                "Should throw IllegalArgumentException when HSM alias is not known."
            ) {
                service.deriveSharedSecret(
                    SharedSecretAliasSpec(
                        publicKey = key.publicKey,
                        hsmAlias = compliance.generateRandomIdentifier(),
                        keyScheme = keyScheme,
                        otherPublicKey = encryptedData.publicKey
                    ),
                    mapOf(CRYPTO_TENANT_ID to tenantId)
                )
            }
            spec
        }
        val result = decryptWithStableKeyPair(
            schemeMetadata = schemeMetadata,
            salt = encryptedData.params.salt,
            publicKey = key.publicKey,
            otherPublicKey = encryptedData.publicKey,
            cipherText = encryptedData.cipherText,
            aad = null
        ) {
            service.deriveSharedSecret(spec, mapOf(CRYPTO_TENANT_ID to tenantId))
        }
        assertArrayEquals(plainText, result)
    }

    private fun Collection<SigningExperiment>.validateAndCleanupKeys(generatedKeys: Collection<PublicKey>) {
        try {
            forEach {
                `Should be able to encode and then decode public key as byte array`(it.key.publicKey)
                `Should be able to encode and then decode public key as PEM`(it.key.publicKey)
                if (canBeUsedInCert(it.keyScheme)) {
                    `Should be able to persist and then load public key from key store`(it.key.publicKey)
                }
                assertTrue(
                    verifier.isValid(
                        publicKey = it.key.publicKey,
                        signatureSpec = it.signatureSpec,
                        signatureData = it.result,
                        clearData = it.clearData
                    ),
                    "Signature validation failed."
                )
            }
            assertEquals(
                generatedKeys.size,
                generatedKeys.distinct().size,
                "All generated keys must be distinct"
            )
        } finally {
            cleanupSigningKeyPairs()
        }
    }

    private fun Collection<GeneratedKey>.encodeDecodeAndCleanupKeys() {
        try {
            forEach {
                `Should be able to encode and then decode public key as byte array`(it.publicKey)
                `Should be able to encode and then decode public key as PEM`(it.publicKey)
            }
            assertEquals(
                size,
                distinct().size,
                "All generated keys must be distinct"
            )
        } finally {
            cleanupKeyPairs()
        }
    }

    private fun Collection<SigningExperiment>.cleanupSigningKeyPairs() {
        map { it.key }.cleanupKeyPairs()
    }

    private fun Collection<GeneratedKey>.cleanupKeyPairs() {
        val candidates = mapNotNull {
            (it as? GeneratedPublicKey)?.hsmAlias
        }.distinct()
        logger.info(
            "Considering {} keys for deleting [{}])",
            candidates.size,
            candidates.joinToString()
        )
        candidates.forEach {
            deleteKeyPair(it)
        }
    }

    private fun `Should be able to encode and then decode public key as byte array`(publicKey: PublicKey) {
        val decodedByteArrayPublicKey = schemeMetadata.decodePublicKey(
            schemeMetadata.encodeAsByteArray(publicKey)
        )
        assertEquals(
            publicKey,
            decodedByteArrayPublicKey,
            "Encoded as byte array and then decoded key must equal the original key."
        )
    }

    private fun `Should be able to encode and then decode public key as PEM`(publicKey: PublicKey) {
        val decodedPEMPublicKey = schemeMetadata.decodePublicKey(
            schemeMetadata.encodeAsString(publicKey)
        )
        assertEquals(
            publicKey,
            decodedPEMPublicKey,
            "Encoded as PEM and then decoded key must equal the original key."
        )
    }

    private fun `Should be able to persist and then load public key from key store`(
        publicKey: PublicKey
    ) {
        val subjectAlias = compliance.generateRandomIdentifier()
        val caKeyPair = generateCAKeyPair()
        val password = compliance.generateRandomIdentifier().toCharArray()
        val keyStore = KeyStore.getInstance("JKS")
        keyStore.load(null, password)
        val jksFile = ByteArrayOutputStream().use {
            keyStore.setCertificateEntry(
                subjectAlias, createDevCertificate(
                    issuer = X500Name("CN=Bob, O=CA, L=Dublin, C=Ireland"),
                    signer = object : ContentSigner {
                        private val baos = ByteArrayOutputStream()
                        override fun getAlgorithmIdentifier(): AlgorithmIdentifier =
                            DefaultSignatureOIDMap.EDDSA_ED25519

                        override fun getOutputStream(): OutputStream = baos
                        override fun getSignature(): ByteArray = signByCA(caKeyPair, baos.toByteArray())
                    },
                    subject = X500Name("CN=Alice, O=R3, L=Dublin, C=Ireland"),
                    subjectPublicKey = publicKey
                )
            )
            keyStore.store(it, password)
            it
        }.toByteArray()
        val keyStoreRead = KeyStore.getInstance("JKS")
        val loadedKey = jksFile.inputStream().use {
            keyStoreRead.load(it, password)
            schemeMetadata.decodePublicKey(keyStoreRead.getCertificate(subjectAlias).publicKey.encoded)
        }
        assertEquals(
            publicKey,
            loadedKey,
            "Stored and loaded from key store the key must equal the original key."
        )
    }

    private fun canBeUsedInCert(keyScheme: KeyScheme): Boolean =
        RSA_TEMPLATE.algorithmOIDs.intersect(keyScheme.algorithmOIDs.toSet()).isNotEmpty() ||
                ECDSA_SECP256R1_TEMPLATE.algorithmOIDs.intersect(keyScheme.algorithmOIDs.toSet()).isNotEmpty() ||
                EDDSA_ED25519_TEMPLATE.algorithmOIDs.intersect(keyScheme.algorithmOIDs.toSet()).isNotEmpty()

    private fun generateCAKeyPair(): KeyPair {
        val scheme = schemeMetadata.findKeyScheme(EDDSA_ED25519_CODE_NAME)
        val keyPairGenerator = KeyPairGenerator.getInstance(
            scheme.algorithmName,
            schemeMetadata.providers.getValue(scheme.providerName)
        )
        if (scheme.algSpec != null) {
            keyPairGenerator.initialize(scheme.algSpec, schemeMetadata.secureRandom)
        } else if (scheme.keySize != null) {
            keyPairGenerator.initialize(scheme.keySize!!, schemeMetadata.secureRandom)
        }
        return keyPairGenerator.generateKeyPair()
    }

    private fun signByCA(keyPair: KeyPair, data: ByteArray): ByteArray {
        val scheme = schemeMetadata.findKeyScheme(keyPair.public)
        val signature = Signature.getInstance(
            SignatureSpec.EDDSA_ED25519.signatureName,
            schemeMetadata.providers[scheme.providerName]
        )
        signature.initSign(keyPair.private, schemeMetadata.secureRandom)
        signature.update(data)
        return signature.sign()
    }

    private fun createDevCertificate(
        issuer: X500Name,
        signer: ContentSigner,
        subject: X500Name,
        subjectPublicKey: PublicKey
    ): X509Certificate {
        val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(subjectPublicKey.encoded))
        val validityWindow = getValidityWindow()
        val v3CertGen = X509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(System.currentTimeMillis()),
            Time(validityWindow.first),
            Time(validityWindow.second),
            subject,
            subjectPublicKeyInfo
        )
        return v3CertGen.build(signer).toJca()
    }

    private fun getValidityWindow(): Pair<Date, Date> {
        val startOfDayUTC = Instant.now().truncatedTo(ChronoUnit.DAYS)
        val notBefore = startOfDayUTC - Duration.ZERO
        val notAfter = startOfDayUTC + Duration.ofDays(365)
        return Pair(Date(notBefore.toEpochMilli()), Date(notAfter.toEpochMilli()))
    }

    private fun X509CertificateHolder.toJca(): X509Certificate =
        requireNotNull(
            CertificateFactory.getInstance("X.509").generateCertificate(
                encoded.inputStream()
            ) as? X509Certificate
        ) {
            "Not an X.509 certificate: $this"
        }
}
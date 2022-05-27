package net.corda.crypto.tck.impl.compliance

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.DefaultSignatureOIDMap
import net.corda.crypto.tck.impl.CryptoServiceProviderMap
import net.corda.crypto.tck.impl.ComplianceSpecExtension
import net.corda.crypto.tck.impl.ComplianceSpec
import net.corda.test.util.createTestCase
import net.corda.v5.cipher.suite.CRYPTO_CATEGORY
import net.corda.v5.cipher.suite.CRYPTO_TENANT_ID
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SignatureVerificationService
import net.corda.v5.cipher.suite.schemes.COMPOSITE_KEY_TEMPLATE
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_TEMPLATE
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_NONE_SIGNATURE_SPEC
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

@ExtendWith(ServiceExtension::class, ComplianceSpecExtension::class)
@Suppress("TooManyFunctions")
class CryptoServiceCompliance : AbstractCompliance() {
    companion object {
        private const val THREAD_COUNT = 10

        @InjectService(timeout = 10000L)
        lateinit var providers: CryptoServiceProviderMap

        @InjectService(timeout = 10000L)
        lateinit var schemeMetadata: CipherSchemeMetadata

        @InjectService(timeout = 10000L)
        lateinit var verifier: SignatureVerificationService
    }

    private lateinit var schemesAndSignatureSpecs: List<Pair<String, SignatureSpec>>

    @BeforeEach
    fun setup(spec: ComplianceSpec) {
        super.setup(spec, providers)
        schemesAndSignatureSpecs = compliance.getFlattenedSchemesAndSignatureSpecs()
    }

    @Test
    fun `Should support at least one key schema supported by scheme matadata`() {
        logger.info(
            "Supported key schemes by service: [${service.supportedSchemes().joinToString { it.codeName }}]"
        )
        logger.info(
            "Supported key schemes by scheme metadata: [${service.supportedSchemes().joinToString { it.codeName }}]"
        )
        val supported = service.supportedSchemes().filter {
            schemeMetadata.schemes.contains(it)
        }
        logger.info("Supported by service and scheme metadata: [${supported.joinToString { it.codeName }}]")
        assertTrue(supported.isNotEmpty(), "The service must support at least one key scheme.")
        assertEquals(
            supported.size,
            compliance.options.signatureSpecs.size,
            "Not all supported schemes are configured for the ${CryptoServiceCompliance::class.simpleName} tests."
        )
        compliance.options.signatureSpecs.keys.forEach {
            assertTrue(
                supported.any { s -> s.codeName == it },
                "The key scheme $it is not among supported."
            )
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should be able to generate key pair with suggested alias then endcode & decode public key and then sign and verify`() {
        val generatedPublicKeys = ConcurrentLinkedQueue<PublicKey>()
        schemesAndSignatureSpecs.forEach { (codeName, signatureSpec) ->
            logger.info("scheme=$codeName, signatureSpec=$signatureSpec")
            val keyScheme = schemeMetadata.findKeyScheme(codeName)
            (0 until THREAD_COUNT).createTestCase {
                val alias = compliance.generateRandomIdentifier()
                val key = `Should generate key with expected key scheme`(alias, masterKeyAlias, keyScheme)
                `Should be able to encode and then decode public key as byte array`(key.publicKey)
                `Should be able to encode and then decode public key as PEM`(key.publicKey)
                if (canBeUsedInCert(keyScheme)) {
                    `Should be able to persist and then load public key from key store`(key.publicKey)
                }
                `Should be able to sign and verify signature`(key, keyScheme, signatureSpec)
                generatedPublicKeys.add(key.publicKey)
            }.runAndValidate()
        }
        assertEquals(
            generatedPublicKeys.size,
            generatedPublicKeys.distinct().size,
            "All generated keys must be distinct"
        )
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should be able to generate key pair without suggested alias, suggesting wrapped key, then endcode & decode public key and then sign and verify`() {
        val generatedPublicKeys = ConcurrentLinkedQueue<PublicKey>()
        schemesAndSignatureSpecs.forEach { (codeName, signatureSpec) ->
            logger.info("scheme=$codeName, signatureSpec=$signatureSpec")
            val keyScheme = schemeMetadata.findKeyScheme(codeName)
            (0 until THREAD_COUNT).createTestCase {
                val key = `Should generate key with expected key scheme`(null, masterKeyAlias, keyScheme)
                `Should be able to encode and then decode public key as byte array`(key.publicKey)
                `Should be able to encode and then decode public key as PEM`(key.publicKey)
                if (canBeUsedInCert(keyScheme)) {
                    `Should be able to persist and then load public key from key store`(key.publicKey)
                }
                `Should be able to sign and verify signature`(key, keyScheme, signatureSpec)
                generatedPublicKeys.add(key.publicKey)
            }.runAndValidate()
        }
        assertEquals(
            generatedPublicKeys.size,
            generatedPublicKeys.distinct().size,
            "All generated keys must be distinct"
        )
    }

    @Test
    fun `Should throw CryptoServiceException when wrapping key is required and already exists`() {
        assumeTrue(!masterKeyAlias.isNullOrBlank())
        assertThrows<CryptoServiceException> {
            service.createWrappingKey(
                masterKeyAlias!!, true, mapOf(
                    CRYPTO_TENANT_ID to CryptoTenants.CRYPTO
                )
            )
        }
    }

    @Test
    fun `Should throw CryptoServiceException when key scheme is not supported`() {
        assertThrows<CryptoServiceException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    keyScheme = COMPOSITE_KEY_TEMPLATE.makeScheme("BC"),
                    alias = compliance.generateRandomIdentifier(),
                    masterKeyAlias = masterKeyAlias,
                    secret = compliance.generateRandomIdentifier().toByteArray()
                ),
                context = mapOf(
                    CRYPTO_TENANT_ID to tenantId,
                    CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                )
            )
        }
        assertThrows<CryptoServiceException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    keyScheme = COMPOSITE_KEY_TEMPLATE.makeScheme("BC"),
                    alias = null,
                    masterKeyAlias = masterKeyAlias,
                    secret = compliance.generateRandomIdentifier().toByteArray()
                ),
                context = mapOf(
                    CRYPTO_TENANT_ID to tenantId,
                    CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                )
            )
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
            EDDSA_ED25519_NONE_SIGNATURE_SPEC.signatureName,
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
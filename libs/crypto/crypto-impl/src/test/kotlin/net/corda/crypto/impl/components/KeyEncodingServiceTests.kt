package net.corda.crypto.impl.components

import net.corda.crypto.impl.generateKeyPair
import net.corda.crypto.impl.signData
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.schemes.COMPOSITE_KEY_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.SignatureVerificationService
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyEncodingServiceTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var keyEncoder: KeyEncodingService
        private lateinit var verifier: SignatureVerificationService

        @TempDir
        @JvmStatic
        lateinit var tempDir: Path

        @JvmStatic
        @BeforeAll
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
            keyEncoder = schemeMetadata
            val digest = DigestServiceImpl(schemeMetadata, null)
            verifier = SignatureVerificationServiceImpl(schemeMetadata, digest)
        }

        @JvmStatic
        fun signatureSchemes(): Array<SignatureScheme> = schemeMetadata.schemes.filter {
            it.codeName != COMPOSITE_KEY_CODE_NAME
        }.toTypedArray()
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(60)
    @Suppress("MaxLineLength")
    fun `Should convert public key to PEM and backand and still to able to use for verification for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val keyPair = generateKeyPair(schemeMetadata, signatureScheme.codeName)
        val encodedPublicKey = keyEncoder.encodeAsString(keyPair.public)
        assert(encodedPublicKey.startsWith("-----BEGIN PUBLIC KEY-----")) { encodedPublicKey }
        assert(encodedPublicKey.contains("-----END PUBLIC KEY-----")) { encodedPublicKey }
        val decodedPublicKey = keyEncoder.decodePublicKey(encodedPublicKey)
        assertEquals(decodedPublicKey, keyPair.public)
        val data = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
        val signature = signData(schemeMetadata, keyPair, data)
        assertTrue(verifier.isValid(decodedPublicKey, signature, data))
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(60)
    @Suppress("MaxLineLength")
    fun `Should convert public key to byte array and back and and still to able to use for verification for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val keyPair = generateKeyPair(schemeMetadata, signatureScheme.codeName)
        val encodedPublicKey = keyEncoder.encodeAsByteArray(keyPair.public)
        val decodedPublicKey = keyEncoder.decodePublicKey(encodedPublicKey)
        assertEquals(decodedPublicKey, keyPair.public)
        val data = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
        val signature = signData(schemeMetadata, keyPair, data)
        assertTrue(verifier.isValid(decodedPublicKey, signature, data))
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(60)
    fun `Should round trip encode CompositeKey to byte array with keys for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val alicePublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val bobPublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val charliePublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey
            .Builder()
            .addKeys(aliceAndBob, charliePublicKey)
            .build(threshold = 1)
        val encoded = keyEncoder.encodeAsByteArray(aliceAndBobOrCharlie)
        val decoded = keyEncoder.decodePublicKey(encoded)
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(60)
    fun `Should round trip encode CompositeKey to PEM with keys for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val alicePublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val bobPublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val charliePublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey
            .Builder()
            .addKeys(aliceAndBob, charliePublicKey)
            .build(threshold = 1)
        val encoded = keyEncoder.encodeAsString(aliceAndBobOrCharlie)
        val decoded = keyEncoder.decodePublicKey(encoded)
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(60)
    fun `Should round trip encode CompositeKey with weighting to byte array with keys for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val alicePublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val bobPublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val charliePublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val aliceAndBob = CompositeKey
            .Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val aliceAndBobOrCharlie = CompositeKey
            .Builder()
            .addKey(aliceAndBob, 3)
            .addKey(charliePublicKey, 2)
            .build(threshold = 3)
        val encoded = keyEncoder.encodeAsByteArray(aliceAndBobOrCharlie)
        val decoded = keyEncoder.decodePublicKey(encoded)
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(60)
    fun `Should round trip encode CompositeKey with weighting to PEM with keys for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val alicePublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val bobPublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val charliePublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val aliceAndBob = CompositeKey
            .Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val aliceAndBobOrCharlie = CompositeKey
            .Builder()
            .addKey(aliceAndBob, 3)
            .addKey(charliePublicKey, 2)
            .build(threshold = 3)
        val encoded = keyEncoder.encodeAsString(aliceAndBobOrCharlie)
        val decoded = keyEncoder.decodePublicKey(encoded)
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @Suppress("TooGenericExceptionThrown")
    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(60)
    fun `Test save to keystore with keys for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val alicePublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val bobPublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val charliePublicKey = generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        val aliceAndBob = CompositeKey
            .Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val aliceAndBobOrCharlie = CompositeKey
            .Builder()
            .addKey(aliceAndBob, 3)
            .addKey(charliePublicKey, 2)
            .build(threshold = 3)
        val subjectAlias = newAlias()
        val pwdArray = "password".toCharArray()
        val jksFile = Files.createFile(tempDir.resolve("$subjectAlias.jks")).toFile()
        val keyStoreSave = KeyStore.getInstance("JKS")
        keyStoreSave.load(null, pwdArray)
        val caKeyPair = generateKeyPair(schemeMetadata, signatureScheme.codeName)
        jksFile.outputStream().use {
            keyStoreSave.setCertificateEntry(
                subjectAlias, createDevCertificate(
                    issuer = X500Name("CN=ISSUER, O=o, L=L, ST=il, C=c"),
                    signer = object : ContentSigner {
                        private val sigAlgID: AlgorithmIdentifier = signatureScheme.signatureSpec.signatureOID
                            ?: throw Exception()
                        private val baos = ByteArrayOutputStream()
                        override fun getAlgorithmIdentifier(): AlgorithmIdentifier = sigAlgID
                        override fun getOutputStream(): OutputStream = baos
                        override fun getSignature(): ByteArray = signData(schemeMetadata, caKeyPair, baos.toByteArray())
                    },
                    subject = X500Name("CN=SUBJECT, O=o, L=L, ST=il, C=c"),
                    subjectPublicKey = aliceAndBobOrCharlie
                )
            )
            keyStoreSave.store(it, pwdArray)
        }
        val keyStoreRead = KeyStore.getInstance("JKS")
        val loadedKey = jksFile.inputStream().use {
            keyStoreRead.load(it, pwdArray)
            schemeMetadata.decodePublicKey(keyStoreRead.getCertificate(subjectAlias).publicKey.encoded)
        }
        assertEquals(aliceAndBobOrCharlie, loadedKey)
    }

    private fun newAlias(): String = UUID.randomUUID().toString()

    private fun createDevCertificate(
        issuer: X500Name,
        signer: ContentSigner,
        subject: X500Name,
        subjectPublicKey: PublicKey
    ): X509Certificate {
        val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(subjectPublicKey.encoded))
        val validityWindow = getValidityWindow(Duration.ZERO, Duration.ofDays(365))
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

    @Suppress("SameParameterValue")
    private fun getValidityWindow(before: Duration, after: Duration): Pair<Date, Date> {
        val startOfDayUTC = Instant.now().truncatedTo(ChronoUnit.DAYS)
        val notBefore = startOfDayUTC - before
        val notAfter = startOfDayUTC + after
        return Pair(Date(notBefore.toEpochMilli()), Date(notAfter.toEpochMilli()))
    }

    private fun X509CertificateHolder.toJca(): X509Certificate =
        requireNotNull(
            CertificateFactory.getInstance("X.509").generateCertificate(
            encoded.inputStream()) as? X509Certificate
        ) {
            "Not an X.509 certificate: $this"
        }
}
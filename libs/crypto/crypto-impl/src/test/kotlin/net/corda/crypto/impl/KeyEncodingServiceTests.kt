package net.corda.crypto.impl

import net.corda.crypto.SigningService
import net.corda.crypto.createDevCertificate
import net.corda.crypto.getSigner
import net.corda.crypto.impl.stubs.CryptoServicesTestFactory
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.schemes.COMPOSITE_KEY_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.SignatureVerificationService
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyEncodingServiceTests {
    companion object {
        private lateinit var factory: CryptoServicesTestFactory
        private lateinit var services: CryptoServicesTestFactory.CryptoServices
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var keyEncoder: KeyEncodingService

        @TempDir
        @JvmStatic
        lateinit var tempDir: Path

        @JvmStatic
        @BeforeAll
        fun setup() {
            schemeMetadata = CipherSchemeMetadataProviderImpl().getInstance()
            factory = CryptoServicesTestFactory(schemeMetadata)
            services = factory.createCryptoServices()
            keyEncoder = schemeMetadata
        }

        @JvmStatic
        fun signatureSchemes(): Array<SignatureScheme> = schemeMetadata.schemes.filter {
            it.codeName != COMPOSITE_KEY_CODE_NAME
        }.toTypedArray()

        private fun getServices(
            defaultSignatureSchemeCodeName: String
        ): Pair<SigningService, SignatureVerificationService> =
            Pair(
                services.createSigningService(
                    schemeMetadata.findSignatureScheme(defaultSignatureSchemeCodeName)
                ),
                factory.verifier
            )
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should convert public key to PEM and backand and still to able to use for verification for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val originalPublicKey = signer.generateKeyPair(services.category, alias)
        val encodedPublicKey = keyEncoder.encodeAsString(originalPublicKey)
        assert(encodedPublicKey.startsWith("-----BEGIN PUBLIC KEY-----")) { encodedPublicKey }
        assert(encodedPublicKey.contains("-----END PUBLIC KEY-----")) { encodedPublicKey }
        val decodedPublicKey = keyEncoder.decodePublicKey(encodedPublicKey)
        assertEquals(decodedPublicKey, originalPublicKey)
        val data = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
        val signature = signer.sign(decodedPublicKey, data)
        assertTrue(verifier.isValid(decodedPublicKey, signature.bytes, data))
        assertEquals(decodedPublicKey, originalPublicKey)
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should convert public key to byte array and back and and still to able to use for verification for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val originalPublicKey = signer.generateKeyPair(services.category, alias)
        val encodedPublicKey = keyEncoder.encodeAsByteArray(originalPublicKey)
        val decodedPublicKey = keyEncoder.decodePublicKey(encodedPublicKey)
        assertEquals(decodedPublicKey, originalPublicKey)
        val data = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
        val signature = signer.sign(decodedPublicKey, data)
        assertTrue(verifier.isValid(decodedPublicKey, signature.bytes, data))
        assertEquals(decodedPublicKey, originalPublicKey)
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    fun `Should round trip encode CompositeKey to byte array with keys for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, _) = getServices(signatureScheme.codeName)
        val alicePublicKey = signer.generateKeyPair(services.category, newAlias())
        val bobPublicKey = signer.generateKeyPair(services.category, newAlias())
        val charliePublicKey = signer.generateKeyPair(services.category, newAlias())
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey.Builder().addKeys(aliceAndBob, charliePublicKey).build(threshold = 1)
        val encoded = keyEncoder.encodeAsByteArray(aliceAndBobOrCharlie)
        val decoded = keyEncoder.decodePublicKey(encoded)
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    fun `Should round trip encode CompositeKey to PEM with keys for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, _) = getServices(signatureScheme.codeName)
        val alicePublicKey = signer.generateKeyPair(services.category, newAlias())
        val bobPublicKey = signer.generateKeyPair(services.category, newAlias())
        val charliePublicKey = signer.generateKeyPair(services.category, newAlias())
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey.Builder().addKeys(aliceAndBob, charliePublicKey).build(threshold = 1)
        val encoded = keyEncoder.encodeAsString(aliceAndBobOrCharlie)
        val decoded = keyEncoder.decodePublicKey(encoded)
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    fun `Should round trip encode CompositeKey with weighting to byte array with keys for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, _) = getServices(signatureScheme.codeName)
        val alicePublicKey = signer.generateKeyPair(services.category, newAlias())
        val bobPublicKey = signer.generateKeyPair(services.category, newAlias())
        val charliePublicKey = signer.generateKeyPair(services.category, newAlias())
        val aliceAndBob = CompositeKey.Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val aliceAndBobOrCharlie = CompositeKey.Builder()
            .addKey(aliceAndBob, 3)
            .addKey(charliePublicKey, 2)
            .build(threshold = 3)
        val encoded = keyEncoder.encodeAsByteArray(aliceAndBobOrCharlie)
        val decoded = keyEncoder.decodePublicKey(encoded)
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    fun `Should round trip encode CompositeKey with weighting to PEM with keys for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, _) = getServices(signatureScheme.codeName)
        val alicePublicKey = signer.generateKeyPair(services.category, newAlias())
        val bobPublicKey = signer.generateKeyPair(services.category, newAlias())
        val charliePublicKey = signer.generateKeyPair(services.category, newAlias())
        val aliceAndBob = CompositeKey.Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val aliceAndBobOrCharlie = CompositeKey.Builder()
            .addKey(aliceAndBob, 3)
            .addKey(charliePublicKey, 2)
            .build(threshold = 3)
        val encoded = keyEncoder.encodeAsString(aliceAndBobOrCharlie)
        val decoded = keyEncoder.decodePublicKey(encoded)
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    fun `Test save to keystore with keys for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, _) = getServices(signatureScheme.codeName)
        val alicePublicKey = signer.generateKeyPair(services.category, newAlias())
        val bobPublicKey = signer.generateKeyPair(services.category, newAlias())
        val charliePublicKey = signer.generateKeyPair(services.category, newAlias())
        val aliceAndBob = CompositeKey.Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val aliceAndBobOrCharlie = CompositeKey.Builder()
            .addKey(aliceAndBob, 3)
            .addKey(charliePublicKey, 2)
            .build(threshold = 3)
        val caAlias = newAlias()
        val subjectAlias = newAlias()
        val pwdArray = "password".toCharArray()
        val jksFile = Files.createFile(tempDir.resolve("$subjectAlias.jks")).toFile()
        val keyStoreSave = KeyStore.getInstance("JKS")
        keyStoreSave.load(null, pwdArray)
        signer.generateKeyPair(services.category, caAlias)
        jksFile.outputStream().use {
            keyStoreSave.setCertificateEntry(
                subjectAlias, createDevCertificate(
                    issuer = X500Name("CN=ISSUER, O=o, L=L, ST=il, C=c"),
                    signer = signer.getSigner(schemeMetadata, caAlias),
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
}
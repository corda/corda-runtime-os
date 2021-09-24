package net.corda.crypto.impl

import net.corda.crypto.CryptoCategories
import net.corda.crypto.SigningService
import net.corda.crypto.testkit.CryptoMocks
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.SignatureVerificationServiceProvider
import net.corda.v5.cipher.suite.schemes.COMPOSITE_KEY_CODE_NAME
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureVerificationService
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.security.SignatureException
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignatureVerificationServiceTests {
    companion object {
        private val testData = UUID.randomUUID().toString().toByteArray()
        private val badVerifyData = UUID.randomUUID().toString().toByteArray()
        private lateinit var memberId: String
        private lateinit var cryptoMocks: CryptoMocks
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var signatureVerificationServiceProvider: SignatureVerificationServiceProvider

        @JvmStatic
        fun signatureSchemes(): Array<SignatureScheme> = schemeMetadata.schemes.filter {
            it.codeName != COMPOSITE_KEY_CODE_NAME
        }.toTypedArray()

        @JvmStatic
        @BeforeAll
        fun setup() {
            memberId = UUID.randomUUID().toString()
            cryptoMocks = CryptoMocks()
            schemeMetadata = cryptoMocks.schemeMetadata
            signatureVerificationServiceProvider = SignatureVerificationServiceProviderImpl()
        }

        @JvmStatic
        fun signatureSchemesWithPrecalculatedDigest(): List<Arguments> =
            schemeMetadata.digests.flatMap { digest ->
                schemeMetadata.schemes.filter { scheme ->
                    scheme.codeName == RSA_CODE_NAME || scheme.codeName == ECDSA_SECP256R1_CODE_NAME
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

        private fun getServices(
            defaultSignatureSchemeCodeName: String
        ): Pair<SigningService, SignatureVerificationService> {
            val factories = cryptoMocks.factories(
                defaultSignatureSchemeCodeName,
                defaultSignatureSchemeCodeName
            )
            return Pair(
                factories.cryptoServices.getSigningService(
                    memberId = memberId,
                    category = CryptoCategories.LEDGER
                ),
                signatureVerificationServiceProvider.getInstance(
                    cryptoMocks.factories.cipherSuite
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    fun `Should verify for all supported signature schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        val signature = signer.sign(alias, testData)
        verifier.verify(publicKey, signature, testData)
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    fun `Should fail verify wrong data for all supported signature schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        val signature = signer.sign(alias, testData)
        assertFailsWith<SignatureException> {
            verifier.verify(publicKey, signature, badVerifyData)
        }
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    fun `Should fail verify empty signature for all supported signature schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        assertFailsWith<IllegalArgumentException> {
            verifier.verify(publicKey, ByteArray(0), testData)
        }
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    fun `Should fail verify for empty clear data for all supported signature schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        val signature = signer.sign(alias, testData)
        assertFailsWith<IllegalArgumentException> {
            verifier.verify(publicKey, signature, ByteArray(0))
        }
    }

    @ParameterizedTest
    @MethodSource("signatureSchemesWithPrecalculatedDigest")
    @Timeout(30)
    fun `Should verify using overload with signature spec for all supported signature schemes`(
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        val signature = signer.sign(alias, signatureSpec, testData)
        verifier.verify(publicKey, signatureSpec, signature, testData)
    }

    @ParameterizedTest
    @MethodSource("signatureSchemesWithPrecalculatedDigest")
    @Timeout(30)
    fun `Should fail verify wrong data using overload with signature spec for all supported signature schemes`(
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        val signature = signer.sign(alias, signatureSpec, testData)
        assertFailsWith<SignatureException> {
            verifier.verify(publicKey, signatureSpec, signature, badVerifyData)
        }
    }

    @ParameterizedTest
    @MethodSource("signatureSchemesWithPrecalculatedDigest")
    @Timeout(30)
    fun `Should fail verify empty signature using overload with signature spec for all supported signature schemes`(
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        assertFailsWith<IllegalArgumentException> {
            verifier.verify(publicKey, signatureSpec, ByteArray(0), testData)
        }
    }

    @ParameterizedTest
    @MethodSource("signatureSchemesWithPrecalculatedDigest")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should fail verify for empty clear data using overload with signature spec for all supported signature schemes`(
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        val signature = signer.sign(alias, signatureSpec, testData)
        assertFailsWith<IllegalArgumentException> {
            verifier.verify(publicKey, signatureSpec, signature, ByteArray(0))
        }
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    fun `Should validate for all supported signature schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        val signature = signer.sign(alias, testData)
        assertTrue(verifier.isValid(publicKey, signature, testData))
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    fun `Should not validate wring data for all supported signature schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        val signature = signer.sign(alias, testData)
        assertFalse(verifier.isValid(publicKey, signature, badVerifyData))
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    fun `Should fail validate empty signature for all supported signature schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        assertFailsWith<IllegalArgumentException> {
            verifier.isValid(publicKey, ByteArray(0), testData)
        }
    }

    @ParameterizedTest
    @MethodSource("signatureSchemes")
    @Timeout(30)
    fun `Should fail validate for empty clear data for all supported signature schemes`(
        signatureScheme: SignatureScheme
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        val signature = signer.sign(alias, testData)
        assertFailsWith<IllegalArgumentException> {
            verifier.isValid(publicKey, signature, ByteArray(0))
        }
    }

    @ParameterizedTest
    @MethodSource("signatureSchemesWithPrecalculatedDigest")
    @Timeout(30)
    fun `Should validate using overload with signature spec for all supported signature schemes`(
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        val signature = signer.sign(alias, signatureSpec, testData)
        assertTrue(verifier.isValid(publicKey, signatureSpec, signature, testData))
    }

    @ParameterizedTest
    @MethodSource("signatureSchemesWithPrecalculatedDigest")
    @Timeout(30)
    fun `Should not validate wring data for using overload with signature spec all supported signature schemes`(
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        val signature = signer.sign(alias, signatureSpec, testData)
        assertFalse(verifier.isValid(publicKey, signatureSpec, signature, badVerifyData))
    }

    @ParameterizedTest
    @MethodSource("signatureSchemesWithPrecalculatedDigest")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should fail validate empty signature using overload with signature spec for all supported signature schemes`(
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        assertFailsWith<IllegalArgumentException> {
            verifier.isValid(publicKey, signatureSpec, ByteArray(0), testData)
        }
    }

    @ParameterizedTest
    @MethodSource("signatureSchemesWithPrecalculatedDigest")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should fail validate for empty clear data using overload with signature spec for all supported signature schemes`(
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec
    ) {
        val (signer, verifier) = getServices(signatureScheme.codeName)
        val alias = newAlias()
        val publicKey = signer.generateKeyPair(alias)
        val signature = signer.sign(alias, testData)
        assertFailsWith<IllegalArgumentException> {
            verifier.isValid(publicKey, signatureSpec, signature, ByteArray(0))
        }
    }

    private fun newAlias(): String = UUID.randomUUID().toString()
}
package net.corda.crypto.core

import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_TEMPLATE
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_TEMPLATE
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_TEMPLATE
import net.corda.v5.cipher.suite.schemes.KeySchemeTemplate
import net.corda.v5.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SM2_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SPHINCS256_TEMPLATE
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.ECDSA_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.ECDSA_SHA384_SIGNATURE_SPEC
import net.corda.v5.crypto.ECDSA_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.EDDSA_ED25519_NONE_SIGNATURE_SPEC
import net.corda.v5.crypto.GOST3410_GOST3411_SIGNATURE_SPEC
import net.corda.v5.crypto.RSASSA_PSS_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.RSASSA_PSS_SHA384_SIGNATURE_SPEC
import net.corda.v5.crypto.RSASSA_PSS_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.RSA_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.RSA_SHA384_SIGNATURE_SPEC
import net.corda.v5.crypto.RSA_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.SM2_SM3_SIGNATURE_SPEC
import net.corda.v5.crypto.SPHINCS256_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.SignatureSpec
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Provider
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import kotlin.test.assertNull
import kotlin.test.assertSame

class DefaultSignatureOIDMapTests {
    companion object {
        private lateinit var defaultProvider: Provider
        private lateinit var postQuantumProvider: Provider
        private lateinit var generatedTestData: List<Arguments>
        private lateinit var rsa: KeyPair
        private lateinit var eddsa: KeyPair
        private lateinit var sphincs: KeyPair
        private lateinit var sm2: KeyPair
        private lateinit var gost: KeyPair
        private lateinit var ecdsak1: KeyPair
        private lateinit var ecdsar1: KeyPair

        @JvmStatic
        @BeforeAll
        @Suppress("MaxLineLength")
        fun setup() {
            defaultProvider = BouncyCastleProvider()
            postQuantumProvider = BouncyCastlePQCProvider()
            rsa = generateKeyPair(RSA_TEMPLATE, defaultProvider)
            eddsa = generateKeyPair(EDDSA_ED25519_TEMPLATE, defaultProvider)
            sphincs = generateKeyPair(SPHINCS256_TEMPLATE, postQuantumProvider)
            sm2 = generateKeyPair(SM2_TEMPLATE, defaultProvider)
            gost = generateKeyPair(GOST3410_GOST3411_TEMPLATE, defaultProvider)
            ecdsak1 = generateKeyPair(ECDSA_SECP256K1_TEMPLATE, defaultProvider)
            ecdsar1 = generateKeyPair(ECDSA_SECP256R1_TEMPLATE, defaultProvider)
            generatedTestData = listOf(
                Arguments.of(eddsa, EDDSA_ED25519_NONE_SIGNATURE_SPEC, DefaultSignatureOIDMap.EDDSA_ED25519),
                Arguments.of(sphincs, SPHINCS256_SHA512_SIGNATURE_SPEC, DefaultSignatureOIDMap.SPHINCS256_SHA512),
                Arguments.of(sm2, SM2_SM3_SIGNATURE_SPEC, DefaultSignatureOIDMap.SM3_SM2),
                Arguments.of(sm2, SignatureSpec("SHA256withSM2"), DefaultSignatureOIDMap.SM3_SHA256),
                Arguments.of(sm2, SignatureSpec("SHA384withSM2"), DefaultSignatureOIDMap.SM3_SHA384),
                Arguments.of(sm2, SignatureSpec("SHA512withSM2"), DefaultSignatureOIDMap.SM3_SHA512),
                Arguments.of(sm2, SignatureSpec("WHIRLPOOLwithSM2"), DefaultSignatureOIDMap.SM3_WHIRPOOL),
                Arguments.of(sm2, SignatureSpec("BLAKE2B256withSM2"), DefaultSignatureOIDMap.SM3_BLAKE2B256),
                Arguments.of(sm2, SignatureSpec("BLAKE2B512withSM2"), DefaultSignatureOIDMap.SM3_BLAKE2B512),
                Arguments.of(gost, GOST3410_GOST3411_SIGNATURE_SPEC, DefaultSignatureOIDMap.GOST3410_GOST3411),
                Arguments.of(rsa, RSA_SHA256_SIGNATURE_SPEC, DefaultSignatureOIDMap.SHA256_RSA),
                Arguments.of(rsa, RSA_SHA384_SIGNATURE_SPEC, DefaultSignatureOIDMap.SHA384_RSA),
                Arguments.of(rsa, RSA_SHA512_SIGNATURE_SPEC, DefaultSignatureOIDMap.SHA512_RSA),
                Arguments.of(rsa, SignatureSpec("SHA256WITHRSAANDMGF1"), DefaultSignatureOIDMap.SHA256_RSASSA_PSS),
                Arguments.of(rsa, SignatureSpec("SHA384WITHRSAANDMGF1"), DefaultSignatureOIDMap.SHA384_RSASSA_PSS),
                Arguments.of(rsa, SignatureSpec("SHA512WITHRSAANDMGF1"), DefaultSignatureOIDMap.SHA512_RSASSA_PSS),
                Arguments.of(rsa, RSASSA_PSS_SHA256_SIGNATURE_SPEC, DefaultSignatureOIDMap.SHA256_RSASSA_PSS),
                Arguments.of(rsa, RSASSA_PSS_SHA384_SIGNATURE_SPEC, DefaultSignatureOIDMap.SHA384_RSASSA_PSS),
                Arguments.of(rsa, RSASSA_PSS_SHA512_SIGNATURE_SPEC, DefaultSignatureOIDMap.SHA512_RSASSA_PSS),
                Arguments.of(
                    rsa,
                    SignatureSpec(
                        "RSASSA-PSS",
                        PSSParameterSpec(
                            "SHA-256",
                            "MGF1",
                            MGF1ParameterSpec.SHA256,
                            16,
                            1
                        )
                    ),
                    null
                ),
                Arguments.of(
                    rsa,
                    SignatureSpec(
                        "RSASSA-PSS",
                        PSSParameterSpec(
                            "SHA-384",
                            "MGF1",
                            MGF1ParameterSpec.SHA384,
                            16,
                            1
                        )
                    ),
                    null
                ),
                Arguments.of(
                    rsa,
                    SignatureSpec(
                        "RSASSA-PSS",
                        PSSParameterSpec(
                            "SHA-512",
                            "MGF1",
                            MGF1ParameterSpec.SHA512,
                            16,
                            1
                        )
                    ),
                    null
                ),
                Arguments.of(ecdsak1, ECDSA_SHA256_SIGNATURE_SPEC, DefaultSignatureOIDMap.SHA256_ECDSA_K1),
                Arguments.of(ecdsak1, ECDSA_SHA384_SIGNATURE_SPEC, DefaultSignatureOIDMap.SHA384_ECDSA_K1),
                Arguments.of(ecdsak1, ECDSA_SHA512_SIGNATURE_SPEC, DefaultSignatureOIDMap.SHA512_ECDSA_K1),
                Arguments.of(ecdsar1, ECDSA_SHA256_SIGNATURE_SPEC, DefaultSignatureOIDMap.SHA256_ECDSA_R1),
                Arguments.of(ecdsar1, ECDSA_SHA384_SIGNATURE_SPEC, DefaultSignatureOIDMap.SHA384_ECDSA_R1),
                Arguments.of(ecdsar1, ECDSA_SHA512_SIGNATURE_SPEC, DefaultSignatureOIDMap.SHA512_ECDSA_R1),
            )
        }

        private fun generateKeyPair(scheme: KeySchemeTemplate, provider: Provider): KeyPair {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                scheme.algorithmName,
                provider
            )
            if (scheme.algSpec != null) {
                keyPairGenerator.initialize(scheme.algSpec)
            } else if (scheme.keySize != null) {
                keyPairGenerator.initialize(scheme.keySize!!)
            }
            return keyPairGenerator.generateKeyPair()
        }

        @JvmStatic
        fun testData(): List<Arguments> = generatedTestData
    }

    @ParameterizedTest
    @MethodSource("testData")
    fun `Should infer all known signature OIDs`(
        keyPair: KeyPair,
        signatureSpec: SignatureSpec,
        expectedAlgorithm: AlgorithmIdentifier?
    ) {
        val result = DefaultSignatureOIDMap.inferSignatureOID(keyPair.public, signatureSpec)
        if (expectedAlgorithm == null) {
            assertNull(result)
        } else {
            assertSame(expectedAlgorithm, result)
        }
    }

    @Test
    fun `Should return null if the SignatureSpec refers to precalculated digest`() {
        assertNull(
            DefaultSignatureOIDMap.inferSignatureOID(
                rsa.public,
                SignatureSpec(
                    signatureName = "NONEwithRSA",
                    customDigestName = DigestAlgorithmName.SHA2_256
                )
            )
        )
        assertNull(
            DefaultSignatureOIDMap.inferSignatureOID(
                eddsa.public,
                SignatureSpec(
                    signatureName = "EdDSA",
                    customDigestName = DigestAlgorithmName.SHA2_256
                )
            )
        )
        assertNull(
            DefaultSignatureOIDMap.inferSignatureOID(
                sphincs.public,
                SignatureSpec(
                    signatureName = "SHA512withSPHINCS256",
                    customDigestName = DigestAlgorithmName.SHA2_256
                )
            )
        )
        assertNull(
            DefaultSignatureOIDMap.inferSignatureOID(
                sm2.public,
                SignatureSpec(
                    signatureName = "SM3withSM2",
                    customDigestName = DigestAlgorithmName.SHA2_256
                )
            )
        )
        assertNull(
            DefaultSignatureOIDMap.inferSignatureOID(
                gost.public,
                SignatureSpec(
                    signatureName = "GOST3411withGOST3410",
                    customDigestName = DigestAlgorithmName.SHA2_256
                )
            )
        )
        assertNull(
            DefaultSignatureOIDMap.inferSignatureOID(
                ecdsak1.public,
                SignatureSpec(
                    signatureName = "SHA256withECDSA",
                    customDigestName = DigestAlgorithmName.SHA2_256
                )
            )
        )
        assertNull(
            DefaultSignatureOIDMap.inferSignatureOID(
                ecdsar1.public,
                SignatureSpec(
                    signatureName = "SHA256withECDSA",
                    customDigestName = DigestAlgorithmName.SHA2_256
                )
            )
        )
    }
}
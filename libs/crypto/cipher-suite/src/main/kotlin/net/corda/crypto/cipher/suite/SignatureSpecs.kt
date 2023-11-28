package net.corda.crypto.cipher.suite

import net.corda.v5.crypto.SignatureSpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

/**
 * This class holds supported [SignatureSpec]s which defines a digital signature scheme.
 */
object SignatureSpecs {

    /**
     * SHA256withRSA [SignatureSpec].
     */
    val RSA_SHA256 = SignatureSpecImpl("SHA256withRSA")

    /**
     * SHA384withRSA [SignatureSpec].
     */
    val RSA_SHA384 = SignatureSpecImpl("SHA384withRSA")

    /**
     * SHA512withRSA [SignatureSpec].
     */
    val RSA_SHA512 = SignatureSpecImpl("SHA512withRSA")

    /**
     * RSASSA-PSS with SHA256 [SignatureSpec].
     */
    val RSASSA_PSS_SHA256 = ParameterizedSignatureSpec(
        "RSASSA-PSS",
        PSSParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            32,
            1
        )
    )

    /**
     * RSASSA-PSS with SHA384 [SignatureSpec].
     */
    val RSASSA_PSS_SHA384 = ParameterizedSignatureSpec(
        "RSASSA-PSS",
        PSSParameterSpec(
            "SHA-384",
            "MGF1",
            MGF1ParameterSpec.SHA384,
            48,
            1
        )
    )

    /**
     * RSASSA-PSS with SHA512 [SignatureSpec].
     */
    val RSASSA_PSS_SHA512 = ParameterizedSignatureSpec(
        "RSASSA-PSS",
        PSSParameterSpec(
            "SHA-512",
            "MGF1",
            MGF1ParameterSpec.SHA512,
            64,
            1
        )
    )

    /**
     * RSASSA-PSS with SHA256 and MGF1 [SignatureSpec].
     */
    val RSA_SHA256_WITH_MGF1 = SignatureSpecImpl("SHA256withRSAandMGF1")

    /**
     * RSASSA-PSS with SHA384 and MGF1 [SignatureSpec].
     */
    val RSA_SHA384_WITH_MGF1 = SignatureSpecImpl("SHA384withRSAandMGF1")

    /**
     * RSASSA-PSS with SHA512 and MGF1 [SignatureSpec].
     */
    val RSA_SHA512_WITH_MGF1 = SignatureSpecImpl("SHA512withRSAandMGF1")

    /**
     * SHA256withECDSA [SignatureSpec].
     */
    val ECDSA_SHA256 = SignatureSpecImpl("SHA256withECDSA")

    /**
     * SHA384withECDSA [SignatureSpec].
     */
    val ECDSA_SHA384 = SignatureSpecImpl("SHA384withECDSA")

    /**
     * SHA512withECDSA [SignatureSpec].
     */
    val ECDSA_SHA512 = SignatureSpecImpl("SHA512withECDSA")

    /**
     * EdDSA [SignatureSpec].
     */
    val EDDSA_ED25519 = SignatureSpecImpl("EdDSA")

    /**
     * SHA512withSPHINCS256 [SignatureSpec].
     */
    val SPHINCS256_SHA512 = SignatureSpecImpl("SHA512withSPHINCS256")

    /**
     * SM3withSM2 [SignatureSpec].
     */
    val SM2_SM3 = SignatureSpecImpl("SM3withSM2")

    /**
     * SHA256withSM2 [SignatureSpec].
     */
    val SM2_SHA256 = SignatureSpecImpl("SHA256withSM2")

    /**
     * GOST3411withGOST3410 [SignatureSpec].
     */
    val GOST3410_GOST3411 = SignatureSpecImpl("GOST3411withGOST3410")
}

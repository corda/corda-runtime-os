package net.corda.v5.crypto

import net.corda.v5.base.annotations.CordaSerializable
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

/**
 * This class is used to define a digital signature scheme.
 *
 * @param signatureName The signature-scheme name as required to create [java.security.Signature]
 * objects (e.g. "SHA256withECDSA").
 *
 * When used for signing the [signatureName] must match the corresponding key scheme, e.g. you cannot use
 * "SHA256withECDSA" with "RSA" keys.
 */
@CordaSerializable
open class SignatureSpec(
    /**
     * The signature-scheme name as required to create [java.security.Signature] objects (e.g. "SHA256withECDSA").
     */
    val signatureName: String
) {
    companion object {
        /**
         * SHA256withRSA [SignatureSpec].
         */
        @JvmField
        val RSA_SHA256 = SignatureSpec(
            signatureName = "SHA256withRSA"
        )

        /**
         * SHA384withRSA [SignatureSpec].
         */
        @JvmField
        val RSA_SHA384 = SignatureSpec(
            signatureName = "SHA384withRSA"
        )

        /**
         * SHA512withRSA [SignatureSpec].
         */
        @JvmField
        val RSA_SHA512 = SignatureSpec(
            signatureName = "SHA512withRSA"
        )

        /**
         * RSASSA-PSS with SHA256 [SignatureSpec].
         */
        @JvmField
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
        @JvmField
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
        @JvmField
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
        @JvmField
        val RSA_SHA256_WITH_MGF1 = SignatureSpec("SHA256withRSAandMGF1")

        /**
         * RSASSA-PSS with SHA384 and MGF1 [SignatureSpec].
         */
        @JvmField
        val RSA_SHA384_WITH_MGF1 = SignatureSpec("SHA384withRSAandMGF1")

        /**
         * RSASSA-PSS with SHA512 and MGF1 [SignatureSpec].
         */
        @JvmField
        val RSA_SHA512_WITH_MGF1 = SignatureSpec("SHA512withRSAandMGF1")

        /**
         * SHA256withECDSA [SignatureSpec].
         */
        @JvmField
        val ECDSA_SHA256 = SignatureSpec(
            signatureName = "SHA256withECDSA"
        )

        /**
         * SHA384withECDSA [SignatureSpec].
         */
        @JvmField
        val ECDSA_SHA384 = SignatureSpec(
            signatureName = "SHA384withECDSA"
        )

        /**
         * SHA512withECDSA [SignatureSpec].
         */
        @JvmField
        val ECDSA_SHA512 = SignatureSpec(
            signatureName = "SHA512withECDSA"
        )

        /**
         * EdDSA [SignatureSpec].
         */
        @JvmField
        val EDDSA_ED25519 = SignatureSpec(
            signatureName = "EdDSA"
        )

        /**
         * SHA512withSPHINCS256 [SignatureSpec].
         */
        @JvmField
        val SPHINCS256_SHA512 = SignatureSpec(
            signatureName = "SHA512withSPHINCS256"
        )

        /**
         * SM3withSM2 [SignatureSpec].
         */
        @JvmField
        val SM2_SM3 = SignatureSpec(
            signatureName = "SM3withSM2"
        )

        /**
         * SHA256withSM2 [SignatureSpec].
         */
        @JvmField
        val SM2_SHA256 = SignatureSpec("SHA256withSM2")

        /**
         * GOST3411withGOST3410 [SignatureSpec].
         */
        @JvmField
        val GOST3410_GOST3411 = SignatureSpec(
            signatureName = "GOST3411withGOST3410"
        )
    }

    init {
        require(signatureName.isNotBlank()) { "The signatureName must not be blank." }
    }

    /**
     * Converts a [SignatureSpec] object to a string representation containing the [signatureName].
     */
    override fun toString(): String = signatureName

    override fun equals(other: Any?): Boolean =
        this === other ||
        other is SignatureSpec &&
        other.signatureName == signatureName

    override fun hashCode(): Int = signatureName.hashCode()
}
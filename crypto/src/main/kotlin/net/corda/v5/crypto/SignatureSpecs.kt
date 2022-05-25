@file:JvmName("SignatureSpecs")

package net.corda.v5.crypto

import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

/**
 * NoOp [SignatureSpec]
 */
@JvmField
val NaSignatureSpec: SignatureSpec = SignatureSpec(
    signatureName = "na"
)

/**
 * SHA256withRSA [SignatureSpec]
 */
@JvmField
val RSA_SHA256_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "SHA256withRSA"
)

/**
 * SHA384withRSA [SignatureSpec]
 */
@JvmField
val RSA_SHA384_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "SHA384withRSA"
)

/**
 * SHA512withRSA [SignatureSpec]
 */
@JvmField
val RSA_SHA512_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "SHA512withRSA"
)

/**
 * RSASSA-PSS with SHA256 [SignatureSpec]
 */
@JvmField
val RSASSA_PSS_SHA256_SIGNATURE_SPEC = SignatureSpec(
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
 * RSASSA-PSS with SHA384 [SignatureSpec]
 */
@JvmField
val RSASSA_PSS_SHA384_SIGNATURE_SPEC = SignatureSpec(
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
 * RSASSA-PSS with SHA512 [SignatureSpec]
 */
@JvmField
val RSASSA_PSS_SHA512_SIGNATURE_SPEC = SignatureSpec(
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
 * SHA256withECDSA [SignatureSpec]
 */
@JvmField
val ECDSA_SHA256_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "SHA256withECDSA"
)

/**
 * SHA384withECDSA [SignatureSpec]
 */
@JvmField
val ECDSA_SHA384_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "SHA384withECDSA"
)

/**
 * SHA512withECDSA [SignatureSpec]
 */
@JvmField
val ECDSA_SHA512_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "SHA512withECDSA"
)

/**
 * EdDSA [SignatureSpec]
 */
@JvmField
val EDDSA_ED25519_NONE_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "EdDSA"
)

/**
 * SHA512withSPHINCS256 [SignatureSpec]
 */
@JvmField
val SPHINCS256_SHA512_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "SHA512withSPHINCS256"
)

/**
 * SM3withSM2 [SignatureSpec]
 */
@JvmField
val SM2_SM3_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "SM3withSM2"
)

/**
 * GOST3411withGOST3410 [SignatureSpec]
 */
@JvmField
val GOST3410_GOST3411_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "GOST3411withGOST3410"
)


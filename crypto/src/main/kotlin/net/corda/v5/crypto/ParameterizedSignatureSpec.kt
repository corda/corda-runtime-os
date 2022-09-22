package net.corda.v5.crypto

import java.security.spec.AlgorithmParameterSpec

/**
 * This class is used to define a digital signature scheme which has the additional algorithm parameters,
 * such as RSASSA-PSS
 *
 * @param signatureName A signature-scheme name as required to create [java.security.Signature]
 * objects (e.g. "SHA256withECDSA")
 * @param params Signature parameters. For example, if using RSASSA-PSS, to avoid
 * using the default SHA1, you must specify the signature parameters explicitly.
 *
 * When used for signing the [signatureName] must match the corresponding key scheme, e.g. you cannot use
 * "SHA256withECDSA" with "RSA" keys.
 */
class ParameterizedSignatureSpec(
    signatureName: String,
    val params: AlgorithmParameterSpec
) : SignatureSpec(signatureName) {
    /**
     * Converts a [ParameterizedSignatureSpec] object to a string representation.
     */
    override fun toString(): String = "$signatureName:${params::class.java.simpleName}"
}
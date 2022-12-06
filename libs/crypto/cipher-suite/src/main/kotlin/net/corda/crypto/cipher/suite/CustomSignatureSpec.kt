package net.corda.crypto.cipher.suite

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import java.security.spec.AlgorithmParameterSpec

/**
 * This class is used to define a digital signature scheme where the hash is precalculated before passing it to the
 * signing operation, it may have additional the additional algorithm parameters, such as RSASSA-PSS
 *
 * @param signatureName a signature-scheme name as required to create [java.security.Signature]
 * objects, note that the signatureName should not contain the digest (e.g. "NONEwithECDSA").
 * @param customDigestName digest algorithm name (e.g. "SHA512")
 * @param params optional signature parameters, like if RSASSA-PSS is being used then in order to avoid
 * using the default SHA1 you can specify the signature parameters explicitly.
 *
 * When used for signing the [signatureName] must match the corresponding key scheme, e.g. you cannot use
 * "NONEwithECDSA" with "RSA" keys.
 *
 * Note as the Bouncy Castle library doesn't support NONEwithRSA scheme, you would have to set the [signatureName] to
 * "RSA/NONE/PKCS1Padding", the implementation will calculate hash by using the RSA encryption on the private key
 * and the decryption using the public key.
 */
class CustomSignatureSpec(
    signatureName: String,
    /**
     * Digest algorithm name.
     */
    val customDigestName: DigestAlgorithmName,
    /**
     * Signature parameters.
     */
    val params: AlgorithmParameterSpec?
) : SignatureSpec(signatureName) {

    /**
     * Creates an instance of the [CustomSignatureSpec] with specified signature name and the digest name,
     * [params] are set to null value.
     *
     * @param signatureName A signature-scheme name as required to create [java.security.Signature]
     * objects, note that the signatureName should not contain the digest (e.g. "NONEwithECDSA").
     * @param customDigestName digest algorithm name (e.g. "SHA512")
     */
    constructor(signatureName: String, customDigestName: DigestAlgorithmName)
        : this(signatureName, customDigestName, null)

    /**
     * Converts a [CustomSignatureSpec] object to a string representation.
     */
    override fun toString(): String = if(params != null) {
        "$signatureName:$customDigestName:${params::class.java.simpleName}"
    } else {
        "$signatureName:$customDigestName"
    }
}
package net.corda.v5.cipher.suite

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import java.security.spec.AlgorithmParameterSpec

/**
 * This class is used to define a digital signature scheme where the hash is precalculated before passing it to the
 * signing operation, it may have additional the additional algorithm parameters, such as RSASSA-PSS
 *
 * @property signatureName a signature-scheme name as required to create [java.security.Signature]
 * objects (e.g. "SHA256withECDSA")
 * @property customDigestName an optional digest algorithm name, set to non-null value if the hash should be precalculated
 * before passing to the provider (e.g. "SHA512"), note that the signatureName should not contain the digest
 * (e.g. "NONEwithECDSA").
 * @property params optional signature parameters, like if RSASSA-PSS is being used then in order to avoid
 * using the default SHA1 you must specify the signature parameters explicitly.
 *
 * When used for signing the [signatureName] must match the corresponding key scheme, e.g. you cannot use
 * "SHA256withECDSA" with "RSA" keys.
 *
 * Note as the Bouncy Castle library doesn't support NONEwithRSA scheme, you would have to set the [signatureName] to
 * "RSA/NONE/PKCS1Padding" and the [customDigestName] to likes of "SHA512", the implementation will calculate hash by
 * using the RSA encryption on the private key and the decryption using the public key.
 */
class CustomSignatureSpec(
    signatureName: String,
    val customDigestName: DigestAlgorithmName,
    val params: AlgorithmParameterSpec? = null
) : SignatureSpec(signatureName) {
    override fun toString(): String = if(params != null) {
        "$signatureName:$customDigestName:${params::class.java.simpleName}"
    } else {
        "$signatureName:$customDigestName"
    }
}
package net.corda.v5.crypto

import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.spec.AlgorithmParameterSpec

/**
 * This class is used to define a digital signature scheme.
 * @property signatureName a signature-scheme name as required to create [Signature][java.security.Signature]
 * objects (e.g. "SHA256withECDSA")
 * @property customDigestName an optional digest algorithm name, set to non-null value if the hash should be precalculated
 * before passing to the provider (e.g. "SHA512"), note that the signatureName should not contain the digest
 * (e.g. "NONEwithECDSA").
 * @property params an optional signature parameters, like if RSASSA-PSS is being used then in order to avoid
 * using the default SHA1 you must specify the signature parameters explicitly.
 * @property signatureOID an optional signature algorithm identifier specifier, must be set for specs which are
 * used by [ContentSigner] (to generate self-signed certificates using Bouncy Castle library)
 *
 * When used for signing the [signatureName] must match the corresponding [SignatureScheme], e.g. you cannot use
 * "SHA256withECDSA" with "RSA" keys.
 *
 * If the default implementations are configured, as they use Bouncy Castle library which doesn't support
 * NONEwithRSA scheme, and you want to use precalculated hash you would have to set the [signatureName] to
 * "RSA/NONE/PKCS1Padding" and the [customDigestName] to like "SHA512", the implementation will calculate hash by
 * using the RSA encryption on the private key and the decryption using the public key.
 */
data class SignatureSpec(
    val signatureName: String,
    val params: AlgorithmParameterSpec? = null,
    val customDigestName: DigestAlgorithmName? = null,
    val signatureOID: AlgorithmIdentifier? = null
) {
    init {
        require(signatureName.isNotBlank()) { "The signatureName must not be blank." }
    }

    /**
     * Returns true if the hash should be precalculated before passing to the signing.
     */
    val precalculateHash: Boolean get() = customDigestName != null

    /**
     * Returns signing data, does hashing of required
     */
    fun getSigningData(hashingService: DigestService, data: ByteArray): ByteArray {
        return if (precalculateHash) {
            hashingService.hash(data, customDigestName!!).bytes
        } else {
            data
        }
    }
}
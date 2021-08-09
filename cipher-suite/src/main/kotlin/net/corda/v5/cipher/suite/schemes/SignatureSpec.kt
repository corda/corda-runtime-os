package net.corda.v5.cipher.suite.schemes

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.Signature

/**
 * This class is used to define a digital signature scheme.
 * @param signatureName a signature-scheme name as required to create [Signature] objects (e.g. "SHA256withECDSA")
 * @param customDigestName a digest algorithm name, set to non null value if the hash should be precalculated before passing
 * to the provider (e.g. "SHA512"), note that the signatureName should not contain the digest (e.g. "NONEwithECDSA").
 *
 * When used for signing the [signatureName] must match the corresponding [SignatureScheme], e.g. you cannot use "SHA256withECDSA" with "RSA" keys.
 *
 * If the default implementations are configured, as they use Bouncy Castle library which doesn't support NONEwithRSA scheme, and you
 * want to use precalculated hash you would have to set the [signatureName] to "RSA/NONE/PKCS1Padding" and the [customDigestName] to like "SHA512",
 * the implementation will calculate hash by using the RSA encryption on the private key and the decryption using the public key.
 */
data class SignatureSpec(
    val signatureName: String,
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
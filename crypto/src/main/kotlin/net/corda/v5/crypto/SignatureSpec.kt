package net.corda.v5.crypto

/**
 * This class is used to define a digital signature scheme.
 *
 * @property signatureName a signature-scheme name as required to create [java.security.Signature]
 * objects (e.g. "SHA256withECDSA")
 *
 * When used for signing the [signatureName] must match the corresponding key scheme, e.g. you cannot use
 * "SHA256withECDSA" with "RSA" keys.
 */
open class SignatureSpec(
    val signatureName: String
) {
    init {
        require(signatureName.isNotBlank()) { "The signatureName must not be blank." }
    }

    /**
     * Returns signing data, does hashing of required
     */
    open fun getSigningData(hashingService: DigestService, data: ByteArray): ByteArray = data

    override fun toString(): String = signatureName
}
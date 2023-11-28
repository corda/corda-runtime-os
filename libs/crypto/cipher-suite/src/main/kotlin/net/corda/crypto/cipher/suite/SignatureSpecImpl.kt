package net.corda.crypto.cipher.suite

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SignatureSpec

/**
 * This class is used to define a digital signature scheme.
 */
@CordaSerializable
class SignatureSpecImpl(
    private val signatureName: String
) : SignatureSpec {
    /**
     * The signature-scheme name as required to create [java.security.Signature] objects
     * (for example, `SHA256withECDSA`). Construct a signature spec.

     * @param signatureName The signature-scheme name as required to create {@link java.security.Signature}
     *                      objects (for example, `SHA256withECDSA`).
     *                      <p>
     *                      When used for signing, the [signatureName] must match the corresponding key scheme, for example,
     *                      you cannot use `SHA256withECDSA` with `RSA` keys.
     */

    init {
        require(signatureName.isNotBlank()) { "The signatureName must not be blank." }
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other != null && other is SignatureSpecImpl && other.signatureName == this.signatureName
    }

    override fun toString(): String {
        return this.signatureName
    }

    override fun getSignatureName(): String {
        return this.signatureName
    }

    override fun hashCode(): Int {
        return this.signatureName.hashCode()
    }
}

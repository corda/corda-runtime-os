package net.corda.crypto.cipher.suite

import net.corda.v5.crypto.SignatureSpec
import java.security.spec.AlgorithmParameterSpec

/**
 * This class is used to define a digital signature scheme which has the additional algorithm parameters,
 * such as `RSASSA-PSS`.  Construct a parameterized signature spec.

 * @param signatureName A signature-scheme name as required to create [java.security.Signature]
 *                      objects (for example, `SHA256withECDSA`).
 * @param params        Signature parameters. For example, if using `RSASSA-PSS`, to avoid
 *                      using the default SHA1, you must specify the signature parameters explicitly.
 *
 *                      When used for signing, the `signatureName` must match the corresponding key scheme,
 *                      for example, you cannot use `SHA256withECDSA` with `RSA` keys.
`` */
class ParameterizedSignatureSpec(private val signatureName: String, val params: AlgorithmParameterSpec) : SignatureSpec {

    init {
        require(signatureName.isNotBlank()) { "The signatureName must not be blank." }
    }

    override fun toString(): String {
        return this.signatureName + ':' + params.javaClass.simpleName
    }

    override fun getSignatureName(): String {
        return this.signatureName
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other != null && other is ParameterizedSignatureSpec && other.signatureName == this.signatureName
    }

    override fun hashCode(): Int {
        return this.signatureName.hashCode()
    }
}

package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec

/**
 * Marker interface denoting the signing parameters.
 *
 * @property keyScheme The scheme for the key used for signing operation.
 * @property signatureSpec The [SignatureSpec] (or one of derived classes such as [ParameterizedSignatureSpec] or
 * [CustomSignatureSpec]) to use for signing, such as SHA256withECDSA, etc.
 */
interface SigningSpec {
    val keyScheme: KeyScheme
    val signatureSpec: SignatureSpec
}
package net.corda.crypto.cipher.suite

import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

/**
 * Marker interface denoting the signing parameters.
 *
 * @property publicKey The public key of the pair.
 * @property keyScheme The scheme for the key used for signing operation.
 * @property signatureSpec The [SignatureSpec] (or one of derived classes such as
 * [net.corda.v5.crypto.ParameterizedSignatureSpec] or [CustomSignatureSpec]) to use for signing,
 * such as SHA256withECDSA, etc.
 */
interface SigningSpec {
    val publicKey: PublicKey
    val keyScheme: KeyScheme
    val signatureSpec: SignatureSpec
}
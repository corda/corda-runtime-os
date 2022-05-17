package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.SignatureScheme

/**
 * Marker interface denoting the signing parameters.
 *
 * @property signatureScheme The scheme for the signing operation.
 */
interface SigningSpec {
    val signatureScheme: SignatureScheme
}
package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable

/**
 * Signals that the signature verification has failed. The operation which caused the exception cannot be retried.
 */
@CordaSerializable
class CryptoSignatureException : CryptoException {
    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable?) : super(message, cause)
}
package net.corda.v5.crypto.failures

import net.corda.v5.base.annotations.CordaSerializable

/**
 * Signals that the signature verification has failed.
 */
@CordaSerializable
class CryptoSignatureException : CryptoException {
    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable?) : super(message, cause)
}
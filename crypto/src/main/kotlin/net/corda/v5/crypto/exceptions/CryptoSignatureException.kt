package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable

/**
 * Signals that the signature verification has failed. The operation which caused the exception cannot be retried.
 */
@CordaSerializable
class CryptoSignatureException : CryptoException {
    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message The detailed message.
     */
    constructor(message: String) : super(message)

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message The detailed message.
     * @param cause The cause.
     */
    constructor(message: String, cause: Throwable?) : super(message, cause)
}
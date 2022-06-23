package net.corda.v5.crypto.failures

import net.corda.v5.base.annotations.CordaSerializable

/**
 * Signals that the transient fault handling was attempted but wasn't successful.
 */
@CordaSerializable
class CryptoRetryException : CryptoException {
    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable?) : super(message, cause)
}
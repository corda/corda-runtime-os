package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable

/**
 * Signals that the transient fault handling was attempted but wasn't successful as such
 * the operation cannot be retried anymore.
 */
@CordaSerializable
class CryptoRetryException : CryptoException {
    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable?) : super(message, cause)
}
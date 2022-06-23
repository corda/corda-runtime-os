package net.corda.v5.crypto.failures

import net.corda.v5.base.annotations.CordaSerializable

/**
 * Signals that there is a throttling by a downstream service, such as HSM or any other.
 * The exception and its concrete implementation are not designed to be passed other
 * process boundaries (over the message bus).
 */
@CordaSerializable
abstract class CryptoThrottlingException : CryptoException, CryptoRetryStrategy {
    constructor(message: String) : super(message, true)

    constructor(message: String, cause: Throwable?) : super(message, true, cause)
}
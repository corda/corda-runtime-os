package net.corda.v5.crypto.failures

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException

// Modifying to test SNYK Delta - not to be merged

/**
 * Base exception for all Crypto Library specific exception. Note that the library may throw common exceptions
 * such as [IllegalArgumentException], [IllegalStateException] and others as well. This base class is only
 * for the specific cases when a site throwing exception can provide some useful context about the operation.
 *
 * Note that the approach for the Crypto Library is to use the existing exception where appropriate and use
 * the specific Crypto Library exceptions only to convey additional context about the conditions which lead to
 * the exception.
 *
 * @property isRecoverable the flag specifying whenever the operation throwing the exception could be retried
 * without any intervention by application-level functionality.
 */
@CordaSerializable
open class CryptoException : CordaRuntimeException {
    val isRecoverable: Boolean

    constructor(message: String) : super(message) {
        this.isRecoverable = false
    }

    constructor(message: String, isRecoverable: Boolean) : super(message) {
        this.isRecoverable = isRecoverable
    }

    constructor(message: String, cause: Throwable?) : super(message, cause) {
        this.isRecoverable = false
    }

    constructor(message: String, isRecoverable: Boolean, cause: Throwable?) : super(message, cause) {
        this.isRecoverable = isRecoverable
    }
}

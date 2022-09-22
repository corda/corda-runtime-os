package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Base exception for all Crypto Library specific exception. Note that the library may throw common exceptions
 * such as [IllegalArgumentException], [IllegalStateException] and others as well. This base class is only
 * for the specific cases when a site throwing exception can provide some useful context about the operation.
 *
 * Note that the approach for the Crypto Library is to use the existing exception where appropriate and use
 * the specific Crypto Library exceptions only to convey additional context about the conditions which lead to
 * the exception.
 *
 * @property isRecoverable The flag specifying whenever the operation throwing the exception could be retried
 * without any intervention by application-level functionality.
 */
@CordaSerializable
open class CryptoException : CordaRuntimeException {

    /**
     * If the value is true then the error condition is considered transient and the operation which throws such
     * exceptions can be retried.
     */
    val isRecoverable: Boolean

    /**
     * Constructs a new exception with the specified detail message. The [isRecoverable] is set to false.
     *
     * @param message The detailed message.
     */
    constructor(message: String) : super(message) {
        this.isRecoverable = false
    }

    /**
     * Constructs a new exception with the specified detail message and when it's recoverable.
     *
     * @param message The detailed message.
     * @param isRecoverable The flag specifying whenever the operation throwing the exception could be retried
     * without any intervention by application-level functionality.
     */
    constructor(message: String, isRecoverable: Boolean) : super(message) {
        this.isRecoverable = isRecoverable
    }

    /**
     * Constructs a new exception with the specified detail message and cause. The [isRecoverable] is set to false.
     *
     * @param message The detailed message.
     * @param cause The cause.
     */
    constructor(message: String, cause: Throwable?) : super(message, cause) {
        this.isRecoverable = false
    }

    /**
     * Constructs a new exception with the specified detail message, cause, and when it's recoverable.
     *
     * @param message The detailed message.
     * @param isRecoverable The flag specifying whenever the operation throwing the exception could be retried
     * without any intervention by application-level functionality.
     * @param cause The cause.
     */
    constructor(message: String, isRecoverable: Boolean, cause: Throwable?) : super(message, cause) {
        this.isRecoverable = isRecoverable
    }
}
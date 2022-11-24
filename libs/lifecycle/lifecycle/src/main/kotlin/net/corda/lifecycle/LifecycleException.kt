package net.corda.lifecycle

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * An exception thrown from the lifecycle library.
 *
 * @param message The exception message
 * @param cause The cause of this exception
 */
class LifecycleException(override val message: String, override val cause: Throwable? = null) :
    CordaRuntimeException(message, cause)

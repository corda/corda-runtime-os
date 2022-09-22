package net.corda.v5.application.persistence

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception that encapsulates errors during persistence.
 *
 * @param message The exception message.
 * @param cause Optional throwable that was caught.
 *
 * @constructor Creates a [CordaPersistenceException] with a [cause].
 */
class CordaPersistenceException(message: String, cause: Throwable?) : CordaRuntimeException(message, cause) {

    /**
     * Creates a [CordaPersistenceException] without a [cause].
     *
     * @param message The exception message.
     */
    constructor(message: String) : this(message, null)
}
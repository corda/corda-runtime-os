package net.corda.v5.application.persistence

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception that encapsulates errors during Persistence.
 *
 * @param message the exception message
 * @param cause optional throwable that was caught
 */
class CordaPersistenceException(message: String, cause: Throwable?) : CordaRuntimeException(message, cause) {
    constructor(msg: String) : this(msg, null)
}
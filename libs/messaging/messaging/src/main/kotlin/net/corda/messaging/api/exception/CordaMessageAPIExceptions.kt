package net.corda.messaging.api.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Fatal error occurred that is not recoverable
 */
class CordaMessageAPIFatalException : CordaRuntimeException {
    constructor(message: String?, exception: Exception) : super(message, exception)
    constructor(message: String?) : super(message)
}

/**
 * Intermittent error during operation which can be retried.
 */
class CordaMessageAPIIntermittentException : CordaRuntimeException {
    constructor(message: String?, exception: Exception) : super(message, exception)
    constructor(message: String?) : super(message)
}

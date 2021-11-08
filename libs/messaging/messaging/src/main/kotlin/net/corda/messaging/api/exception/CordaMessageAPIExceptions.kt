package net.corda.messaging.api.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Fatal error occurred that is not recoverable
 */
open class CordaMessageAPIFatalException(message: String?, exception: Exception? = null) :
    CordaRuntimeException(message, exception)

/**
 * Intermittent error during operation which can be retried.
 */
open class CordaMessageAPIIntermittentException(message: String?, exception: Exception? = null) :
    CordaRuntimeException(message, exception)
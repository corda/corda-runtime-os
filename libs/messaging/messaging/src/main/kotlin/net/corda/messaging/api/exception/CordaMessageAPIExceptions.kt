package net.corda.messaging.api.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Fatal error occurred that is not recoverable
 */
open class CordaMessageAPIFatalException(message: String?, exception: Exception? = null) :
    CordaRuntimeException(message, exception)

/**
 * Error occurred for an individual record that should be sent to the DLQ
 */
open class CordaMessageAPIDLQException(message: String?, exception: Exception? = null) :
    CordaRuntimeException(message, exception)

/**
 * Error occurred for an individual record that should be skipt
 */
open class CordaMessageAPISkipRecordException(message: String?, exception: Exception? = null) :
    CordaRuntimeException(message, exception)

/**
 * Intermittent error during operation which can be retried.
 */
open class CordaMessageAPIIntermittentException(message: String?, exception: Exception? = null) :
    CordaRuntimeException(message, exception)
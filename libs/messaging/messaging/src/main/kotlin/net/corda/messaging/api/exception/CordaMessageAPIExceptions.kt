package net.corda.messaging.api.exception

import java.lang.Exception
import java.lang.RuntimeException

/**
 * Fatal error occurred that is not recoverable
 */
class CordaMessageAPIFatalException (message: String, exception: Exception) : RuntimeException(message, exception)

/**
 * Intermittent error during operation which can be retried.
 */
class CordaMessageAPIIntermittentException (message: String, exception: Exception) : RuntimeException(message, exception)
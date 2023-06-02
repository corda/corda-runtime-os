package net.corda.messaging.api.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Fatal error occurred that is not recoverable. This indicates something fundamental about this process interacting
 * with the message bus was detected. For example the process has been fenced as it was replaced by another, or the
 * version of the message bus is not compatible with Corda.
 */
class CordaMessageAPIFatalException(message: String?, exception: Exception? = null) :
    CordaRuntimeException(message, exception)

/**
 * Intermittent error during operation which can be retried. If this exception is thrown from a producer, the producer
 * is safe to reuse as any transaction will have been aborted on the client's behalf.
 */
open class CordaMessageAPIIntermittentException(message: String?, exception: Exception? = null) :
    CordaRuntimeException(message, exception)

/**
 * Only thrown from a producer. In this case the error is not fatal in the way [CordaMessageAPIFatalException] is, but
 * the producer must be closed and re-instantiated. Re-using the producer when this is thrown is not an option and
 * results in undefined behaviour. Subclass of [CordaMessageAPIIntermittentException] so if you are resetting the
 * producer on all intermittent exceptions you don't need to handle this explicitly.
 */
class CordaMessageAPIProducerRequiresReset(message: String?, exception: Exception? = null) :
    CordaMessageAPIIntermittentException(message, exception)

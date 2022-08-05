package net.corda.flow.pipeline.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * The [FlowTransientException] is thrown for a recoverable error, this exception will cause the event processing
 * to be retried
 */
class FlowTransientException(override val message: String, cause: Throwable? = null) :
    CordaRuntimeException(message, cause)


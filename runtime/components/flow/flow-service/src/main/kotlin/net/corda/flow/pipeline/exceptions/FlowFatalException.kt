package net.corda.flow.pipeline.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * The [FlowFatalException] is thrown for an unrecoverable error, this exception signals the flow can't continue
 * and should be moved to the DLQ
 */
class FlowFatalException(override val message: String, cause: Throwable? = null) :
    CordaRuntimeException(message, cause)


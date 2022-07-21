package net.corda.flow.pipeline.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * The [FlowPlatformException] is thrown for errors that need to be reported back to user code.
 */
class FlowPlatformException(override val message: String, cause: Throwable? = null) :
    CordaRuntimeException(message, cause)
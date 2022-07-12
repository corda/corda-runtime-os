package net.corda.flow.pipeline.exceptions

import net.corda.flow.pipeline.FlowEventContext

/**
 * The [FlowPlatformException] is thrown for errors that need to be reported back to user code.
 */
class FlowPlatformException(message: String, flowEventContext: FlowEventContext<*>, cause: Throwable? = null) :
    FlowProcessingException(message, flowEventContext, cause)
package net.corda.flow.pipeline.exceptions

import net.corda.flow.pipeline.FlowEventContext

/**
 * The [FlowTransientException] is thrown for a recoverable error, this exception will cause the event processing
 * to be retried
 */
class FlowTransientException(message: String, flowEventContext: FlowEventContext<*>, cause: Throwable? = null) :
    FlowProcessingException(message, flowEventContext, cause)


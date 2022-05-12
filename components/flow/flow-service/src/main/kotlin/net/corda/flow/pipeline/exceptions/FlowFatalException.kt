package net.corda.flow.pipeline.exceptions

import net.corda.flow.pipeline.FlowEventContext

/**
 * The [FlowFatalException] is thrown for an unrecoverable error, this exception signals the flow can't continue
 * and should be moved to the DLQ
 */
class FlowFatalException(message: String, flowEventContext: FlowEventContext<Any>, cause: Throwable? = null) :
    FlowProcessingException(message, flowEventContext, cause)


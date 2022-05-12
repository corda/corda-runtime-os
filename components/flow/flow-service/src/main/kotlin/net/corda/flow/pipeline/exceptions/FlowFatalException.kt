package net.corda.flow.pipeline.exceptions

import net.corda.flow.pipeline.FlowEventContext

class FlowFatalException(message: String, flowEventContext: FlowEventContext<Any>, cause: Throwable? = null) :
    FlowProcessingException(message, flowEventContext, cause)


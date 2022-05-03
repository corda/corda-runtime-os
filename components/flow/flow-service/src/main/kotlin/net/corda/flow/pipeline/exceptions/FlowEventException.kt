package net.corda.flow.pipeline.exceptions

import net.corda.flow.pipeline.FlowEventContext

class FlowEventException(message: String?, flowEventContext: FlowEventContext<Any>, cause: Throwable? = null) :
    FlowProcessingException(message, flowEventContext, cause)
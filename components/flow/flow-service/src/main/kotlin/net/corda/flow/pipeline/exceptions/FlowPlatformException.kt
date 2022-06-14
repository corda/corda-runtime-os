package net.corda.flow.pipeline.exceptions

import net.corda.flow.pipeline.FlowEventContext

class FlowPlatformException(message: String, flowEventContext: FlowEventContext<*>, cause: Throwable? = null) :
    FlowProcessingException(message, flowEventContext, cause)
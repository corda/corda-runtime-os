package net.corda.flow.pipeline.exceptions

import net.corda.flow.pipeline.FlowEventContext
import net.corda.v5.base.exceptions.CordaRuntimeException

// need to refactor and remove this once all types of flow exception have been implemented
open class FlowProcessingException(
    message: String?,
    val flowEventContext: FlowEventContext<*>? = null,
    cause: Throwable? = null
) : CordaRuntimeException(message, cause)


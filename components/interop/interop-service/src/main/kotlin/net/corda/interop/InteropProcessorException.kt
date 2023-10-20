package net.corda.interop

import net.corda.data.interop.InteropState
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.v5.base.exceptions.CordaRuntimeException

class InteropProcessorException(
    msg: String,
    val state: StateAndEventProcessor.State<InteropState>?,
    cause: Throwable? = null
) : CordaRuntimeException(msg, cause)

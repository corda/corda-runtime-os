package net.corda.messaging.mediator.processor

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.v5.base.exceptions.CordaRuntimeException

class EventProcessorSyncEventsFatalException(
    val partiallyProcessedState: StateAndEventProcessor.State<*>?,
    cause: Throwable
) : CordaRuntimeException(cause.message, cause)
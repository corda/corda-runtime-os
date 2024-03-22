package net.corda.messaging.mediator.processor

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception thrown when something fatal occurs when trying to process sync events during mediator event processing.
 */
class EventProcessorSyncEventsFatalException(
    val partiallyProcessedState: StateAndEventProcessor.State<*>?,
    cause: Throwable
) : CordaRuntimeException(cause.message, cause)
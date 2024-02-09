package net.corda.messaging.mediator.processor

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * This exception indicates an intermittent exception happened during processing of synchronous events.
 *
 * It contains a partially processed state that can be used for cleanup purposes and marked as failed.
 *
 * @param partiallyProcessedState the state with accumulated changes before processing failure
 * @param cause the throwable that caused the exception
 */
class EventProcessorSyncEventsIntermittentException(
    val partiallyProcessedState: StateAndEventProcessor.State<*>?,
    cause: Throwable
) : CordaRuntimeException(cause.message, cause)
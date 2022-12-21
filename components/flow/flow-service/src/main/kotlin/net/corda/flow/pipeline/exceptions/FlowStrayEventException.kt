package net.corda.flow.pipeline.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * The [FlowStrayEventException] is thrown when event processing needs to be aborted, and no state and output messages
 * should be published. It indicates this event is malformed or operates on a state Corda doesn't currently have
 * knowledge of and thus cannot interact with that state. This exception should be thrown in place of
 * [FlowEventException] in these cases to avoid writing an erroneous null state. If [FlowEventException] was to be thrown
 * instead Corda would write a null state for a Flow it thought was non-existent, effectively 'inventing' a Flow
 * from nothing. If this flow actually existed in Kafka, but Corda was for some reason not yet aware of it, this stray
 * event would have the effect of killing it.
 */
class FlowStrayEventException(override val message: String, cause: Throwable? = null) :
    CordaRuntimeException(message, cause)

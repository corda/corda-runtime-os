package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException

/**
 * The [FlowEventHandler] interface is implemented by services that process [FlowEvent]s received from the message bus.
 *
 * @param T The type of event that the [FlowEventHandler] handles. [T] is equivalent to [FlowEvent.payload] (which returns [Object] and
 * prevents [T] from being more restrictive).
 */
interface FlowEventHandler<T> {

    /**
     * Gets the event type [Class] that the handler accepts.
     */
    val type: Class<T>

    /**
     * Performs pre-processing when receiving a [FlowEvent].
     *
     * Pre-processing is executed as the first step and occurs before starting or resuming a flow's fiber.
     *
     * The [FlowEventContext] returned from this method must have a non-null [Checkpoint]. The [Checkpoint] could be set on the input
     * [context] and modified or a new [Checkpoint] instance can be created and referenced from the output [FlowEventContext].
     *
     * @param context The [FlowEventContext] that should be modified within this processing step.
     *
     * @return The modified [FlowEventContext].
     */
    fun preProcess(context: FlowEventContext<T>): FlowEventContext<T>
}

/**
 * Throws a [FlowProcessingException] if the passed in [context]'s checkpoint is null.
 *
 * @param context The [FlowEventContext] with a possibly null [Checkpoint].
 *
 * @return A non-null [Checkpoint].
 *
 * @throws FlowProcessingException if the passed in [FlowEventContext.checkpoint] is null.
 */
fun FlowEventHandler<*>.requireCheckpoint(context: FlowEventContext<*>): Checkpoint {
    return context.checkpoint ?: throw FlowProcessingException("${this::class.java.name} requires a non-null checkpoint as input")
}
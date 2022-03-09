package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.flow.manager.impl.pipeline.FlowEventPipelineImpl

/**
 * The [FlowEventHandler] interface is implemented by services that process received [FlowEvent]s.
 *
 * The processing of an event is split into steps which are executed by [FlowEventPipelineImpl].
 *
 * Each step receives an incoming [FlowEventContext] that contains information about the the received event, the flow's [Checkpoint] and
 * records that should be sent back to the message bus. It is expected that the [FlowEventContext.checkpoint] and
 * [FlowEventContext.outputRecords] are modified in the handler's steps.
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
 * @throws FlowProcessingException if the passed in [checkpoint] is null.
 */
fun FlowEventHandler<*>.requireCheckpoint(context: FlowEventContext<*>): Checkpoint {
    return context.checkpoint ?: throw FlowProcessingException("${this::class.java.name} requires a non-null checkpoint as input")
}

/**
 * Throws a [FlowProcessingException] if [StateMachineState.waitingFor] is null.
 *
 * @return A non-null [WaitingFor].
 *
 * @throws FlowProcessingException if [StateMachineState.waitingFor] is null.
 */
fun StateMachineState.requireWaitingFor(): WaitingFor {
    return waitingFor
        ?: throw FlowProcessingException("Flow is in an invalid state as it is waiting for no input events while also being suspended")
}
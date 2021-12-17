package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.FlowEventPipeline
import net.corda.flow.manager.impl.handlers.FlowProcessingException

/**
 * The [FlowEventHandler] interface is implemented by services that process received [FlowEvent]s.
 *
 * The processing of an event is split into steps which are executed by [FlowEventPipeline].
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
     * @param context The [FlowEventContext] that should be modified within this processing step.
     *
     * @return The modified [FlowEventContext].
     */
    fun preProcess(context: FlowEventContext<T>): FlowEventContext<T>

    /**
     * Determines whether a received [FlowEvent] should cause a flow to run (includes starting a new fiber or resuming an existing fiber).
     *
     * This method should not modify [FlowEventContext], which should be done in [preProcess] instead.
     *
     * The following should be returned:
     *
     * - [FlowContinuation.Run] - A new fiber should be started or an existing fiber should be resumed. When resumed
     * [FlowContinuation.Run.value] is passed to the [FlowFiber].
     * - [FlowContinuation.Error] - An existing fiber should be resumed and be passed [FlowContinuation.Error.exception].
     * - [FlowContinuation.Continue] - The flow should not be started or resumed.
     *
     * @param context The [FlowEventContext] that should be inspected to determine whether to start or resume a flow's fiber.
     *
     * @return A [FlowContinuation] that represents whether to start or resume a flow's fiber.
     */
    fun runOrContinue(context: FlowEventContext<T>): FlowContinuation

    /**
     * Performs post-processing when receiving a [FlowEvent].
     *
     * Post-processing is executed as the last step and occurs after a flow has suspended.
     *
     * This step will still execute even if [runOrContinue] returned [FlowContinuation.Continue].
     *
     * @param context The [FlowEventContext] that should be modified within this processing step.
     *
     * @return The modified [FlowEventContext].
     */
    fun postProcess(context: FlowEventContext<T>): FlowEventContext<T>
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
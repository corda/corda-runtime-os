package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.FlowEvent
import net.corda.flow.pipeline.events.FlowEventContext

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
     * It is assumed that the flow is initialised in the checkpoint after the event handler has been executed. For most
     * handlers, this will be true on input and no action is required. When handling events that represent a request to
     * start a flow, the flow state inside the checkpoint object must be initialised before [preProcess] returns.
     *
     * @param context The [FlowEventContext] that should be modified within this processing step.
     *
     * @return The modified [FlowEventContext].
     */
    fun preProcess(context: FlowEventContext<T>): FlowEventContext<T>
}

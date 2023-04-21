package net.corda.flow.pipeline.handlers.waiting

import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.events.FlowEventContext

interface FlowWaitingForHandler<T> {

    /**
     * Gets the event type [Class] that the handler accepts.
     */
    val type: Class<T>

    /**
     * Determines whether a flow should run (includes starting a new fiber or resuming an existing fiber).
     *
     * State within the [FlowEventContext], such as a flow's checkpoint can be directly modified inside this method.
     *
     * The following should be returned:
     *
     * - [FlowContinuation.Run] - A new fiber should be started or an existing fiber should be resumed. When resumed
     * [FlowContinuation.Run.value] is passed to the fiber.
     * - [FlowContinuation.Error] - An existing fiber should be resumed and be passed [FlowContinuation.Error.exception].
     * - [FlowContinuation.Continue] - The flow should not be started or resumed.
     *
     * @param context The [FlowEventContext] that should be inspected to determine whether to start or resume a flow's fiber.
     *
     * @return A [FlowContinuation] that represents whether to start or resume a flow's fiber.
     */
    fun runOrContinue(context: FlowEventContext<*>, waitingFor: T): FlowContinuation
}

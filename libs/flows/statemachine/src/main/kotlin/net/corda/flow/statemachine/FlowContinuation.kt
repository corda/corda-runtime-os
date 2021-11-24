package net.corda.flow.statemachine

sealed class FlowContinuation {

    object Continue : FlowContinuation()

    /**
     * The result of a suspension is wrapped in a [FlowContinuation]. This is set in [HousekeepingState] or [NonSerializableState] which
     * the [FlowFiber] then checks for. If the [FlowContinuation] is not null then the flow should resume, and will do so by taking the result.
     *
     * Will probably want an exception version, potentially an interface?
     */
    data class Run(val value: Any? = Unit) : FlowContinuation()

    data class Error(val exception: Throwable) : FlowContinuation()
}
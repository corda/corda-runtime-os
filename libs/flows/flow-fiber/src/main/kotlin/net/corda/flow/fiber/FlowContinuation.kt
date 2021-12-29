package net.corda.flow.fiber

/**
 * [FlowContinuation] captures the next execution step of a [FlowFiber].
 */
sealed class FlowContinuation {

    /**
     * [Continue] specifies that a flow should continue receiving and processing events and should not run or resume its fiber.
     */
    object Continue : FlowContinuation()

    /**
     * [Run] specifies that a flow should run (start or resume) its fiber. When resuming an existing fiber, [value] is passed into the fiber
     * which is returned as the result of [FlowFiber.suspend].
     *
     * @param value The object to pass into the fiber and return as the result of [FlowFiber.suspend].
     */
    data class Run(val value: Any? = Unit) : FlowContinuation()

    /**
     * [Error] specifies that a flow should resume its fiber with an error.
     *
     * @param exception The exception to pass into the fiber and throw as the result of [FlowFiber.suspend].
     */
    data class Error(val exception: Throwable) : FlowContinuation()
}
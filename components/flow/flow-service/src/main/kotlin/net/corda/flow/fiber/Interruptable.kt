package net.corda.flow.fiber

import net.corda.flow.pipeline.runner.FlowRunner

/**
 * [Interruptable] instances can be asked to interrupt their normal flow, usually when executing concurrently in some
 * other context, e.g. a different thread. It is not guaranteed execution can always be interrupted.
 */
interface Interruptable {
    /**
     * Interrupt the normal execution of this [Interruptable]. It should be assumed that this method is called on a
     * different thread to the normal execution flow of the [Interruptable] and as such ensure the implementation is
     * thread safe.
     */
    fun attemptInterrupt()
}

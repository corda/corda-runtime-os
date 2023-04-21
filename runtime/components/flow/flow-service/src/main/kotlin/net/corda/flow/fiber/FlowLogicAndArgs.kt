package net.corda.flow.fiber

import net.corda.v5.application.flows.Flow
import net.corda.v5.base.annotations.Suspendable

/**
 * Represents the flow logic and args of a flow that can be started at the top level.
 *
 * The args will be provided to the call method of the flow where appropriate.
 */
interface FlowLogicAndArgs {

    /**
     * Retrieve the flow logic. Used by the platform to perform dependency injection and do housekeeping.
     */
    val logic: Flow

    /**
     * Start the flow, providing any required arguments.
     */
    @Suspendable
    fun invoke() : String?
}
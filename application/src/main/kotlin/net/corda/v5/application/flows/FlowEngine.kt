package net.corda.v5.application.flows

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import java.time.Duration
import java.util.*

@DoNotImplement
interface FlowEngine : CordaFlowInjectable {
    /**
     * Returns a wrapped [UUID][java.util.UUID] object that identifies this flow or it's top level instance (i.e. subflows have the same
     * identifier as their parents).
     */
    val flowId: UUID

    /**
     * Returns the x500 name of the current virtual node executing the flow.
     */
    val virtualNodeName: MemberX500Name

    /**
     * Invokes the given subflow. This function returns once the subflow completes successfully with the result
     * returned by that subflow's [Flow.call] method. If the subflow has a progress tracker, it is attached to the
     * current step in this flow's progress tracker.
     *
     * If the subflow is not an initiating flow (i.e. not annotated with [InitiatingFlow]) then it will continue to use
     * the existing sessions this flow has created with its counterparties. This allows for subflows which can act as
     * building blocks for other flows, for example removing the boilerplate of common sequences of sends and receives.
     *
     * @throws FlowException This is either thrown by [subLogic] itself or propagated from any of the remote
     * [Flow]s it communicated with. The subflow can be retried by catching this exception.
     */
    @Suspendable
    fun <R> subFlow(subLogic: Flow<R>): R

    /**
     * Suspends the flow and only wakes it up after at least [duration] time has passed.
     *
     * Warning: long sleeps and in general long running flows are highly discouraged, as there is currently no
     * support for flow migration!
     *
     * @throws FlowException if attempted to sleep for longer than 5 minutes.
     */
    @Suspendable
    fun sleep(duration: Duration)
}


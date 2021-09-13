package net.corda.v5.application.flows.flowservices

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowException
import net.corda.v5.application.flows.FlowExternalOperation
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.KilledFlowException
import net.corda.v5.application.flows.FlowId
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import java.time.Duration

@DoNotImplement
interface FlowEngine : CordaFlowInjectable {
    /**
     * Returns a wrapped [UUID][java.util.UUID] object that identifies this flow or it's top level instance (i.e. subflows have the same
     * identifier as their parents).
     */
    val flowId: FlowId

    /**
     * Returns `true` when the current [Flow] has been killed (has received a command to halt its progress and terminate).
     *
     * Check this property in long-running computation loops to exit a flow that has been killed:
     * ```
     * while (!isKilled) {
     *   // do some computation
     * }
     * ```
     *
     * Ideal usage would include throwing a [KilledFlowException] which will lead to the termination of the flow:
     * ```
     * for (item in list) {
     *   if (isKilled) {
     *     throw KilledFlowException(flowId)
     *   }
     *   // do some computation
     * }
     * ```
     *
     * Note, once the [isKilled] flag is set to `true` the flow may terminate once it reaches the next API function marked with the
     * @[Suspendable] annotation. Therefore, it is possible to write a flow that does not interact with the [isKilled] flag while still
     * terminating correctly.
     */
    val isKilled: Boolean

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
     * Helper function that throws a [KilledFlowException] if the current [Flow] has been killed.
     *
     * Call this function in long-running computation loops to exit a flow that has been killed:
     * ```
     * for (item in list) {
     *   checkFlowIsNotKilled()
     *   // do some computation
     * }
     * ```
     *
     * See the [isKilled] property for more information.
     */
    @Suspendable
    fun checkFlowIsNotKilled()

    /**
     * Helper function that throws a [KilledFlowException] if the current [Flow] has been killed. The provided message is added to the
     * thrown [KilledFlowException].
     *
     * Call this function in long-running computation loops to exit a flow that has been killed:
     * ```
     * for (item in list) {
     *   checkFlowIsNotKilled { "The flow $flowId was killed while iterating through the list of items" }
     *   // do some computation
     * }
     * ```
     *
     * See the [isKilled] property for more information.
     */
    @Suspendable
    fun checkFlowIsNotKilled(lazyMessage: () -> Any)

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

    /**
     * Suspends a flow until [FlowExternalOperation] is completed. Once the [FlowExternalOperation] gets completed the flow will resume and
     * return result [R].
     *
     * @param operation The [FlowExternalOperation] to be executed asynchronously.
     * @return The result [R] of [FlowExternalOperation.execute].
     */
    @Suspendable
    fun <R : Any> await(operation: FlowExternalOperation<R>): R
}


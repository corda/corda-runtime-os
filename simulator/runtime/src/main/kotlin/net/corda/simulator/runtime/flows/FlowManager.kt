package net.corda.simulator.runtime.flows

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.SubFlow

/**
 * Calls the flow or subflow provided, waiting until all flows in this call have finished and closing down all sessions
 * before returning.
 */
interface FlowManager {

    /**
     * Calls the provided flow with the given request.
     *
     * @param requestData The request to provide to the flow.
     * @param flow The flow to call.
     * @return The result of the flow.
     */
    fun call(requestData: ClientRequestBody, flow: ClientStartableFlow) : String

    /**
     * Calls the provided subflow.
     *
     * @param subFlow The subflow to call.
     * @return The result of the subflow.
     */
    fun <R> call(subFlow: SubFlow<R>): R
}

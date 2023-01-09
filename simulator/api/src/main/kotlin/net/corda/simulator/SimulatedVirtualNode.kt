package net.corda.simulator

import net.corda.v5.base.annotations.DoNotImplement

/**
 * A simulated virtual node in which flows can be run.
 */
@DoNotImplement
interface SimulatedVirtualNode : SimulatedNode {

    /**
     * Calls the flow with the given request. Note that this call happens on the calling thread, which will wait until
     * the flow has completed before returning the response.
     *
     * @param input The data to input to the flow.
     *
     * @param return The response from the flow.
     */
    fun callFlow(input: RequestData): String

    /**
     * Calls the flow with the given request and a contextProperties provided by the user. Note that this call happens
     * on the calling thread, which will wait until the flow has completed before returning the response.
     *
     * @param input The data to input to the flow.
     * @param contextPropertiesMap A map of context properties passes to the flow
     *
     * @param return The response from the flow.
     */
    fun callFlow(input: RequestData, contextPropertiesMap: Map<String, String> = emptyMap()): String
}

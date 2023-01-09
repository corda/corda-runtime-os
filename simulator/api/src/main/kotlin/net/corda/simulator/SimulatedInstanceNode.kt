package net.corda.simulator

/**
 * A simulated node in which the flow to be run has already been constructed.
 */
interface SimulatedInstanceNode : SimulatedNode {

    /**
     * Calls the flow with the given request. Note that this call happens on the calling thread, which will wait until
     * the flow has completed before returning the response.
     *
     * @param input The data to input to the flow.
     *
     * @return The response from the flow.
     * @throws IllegalStateException if the flow with which this node was constructed was not a
     * [net.corda.v5.application.flows.RPCStartableFlow].
     */
    fun callInstanceFlow(input: RequestData): String

    /**
     * Calls the flow with the given request and contextProperties provided by the user. Note that this call happens
     * on the calling thread, which will wait until the flow has completed before returning the response.
     *
     * @param input The data to input to the flow.
     * @param contextPropertiesMap A map of context properties passes to the flow
     *
     * @return The response from the flow.
     * @throws IllegalStateException if the flow with which this node was constructed was not a
     * [net.corda.v5.application.flows.RPCStartableFlow].
     */
    fun callInstanceFlow(input: RequestData, contextPropertiesMap: Map<String, String>): String
}

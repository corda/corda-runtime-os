package net.corda.simulator

interface SimulatedInstanceNode : SimulatedNode {

    /**
     * Calls the flow with the given request. Note that this call happens on the calling thread, which will wait until
     * the flow has completed before returning the response.
     *
     * @param input The data to input to the flow.
     * @param flow The flow to be called
     *
     * @return The response from the flow.
     */
    fun callInstanceFlow(input: RequestData): String
}

package net.corda.simulator

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.annotations.DoNotImplement
import java.io.Closeable

/**
 * This is the interface of both Simulator and its runtime delegate. It should not be implemented;
 * instead construct a new [Simulator].
 */
@DoNotImplement
interface SimulatedCordaNetwork : Closeable {

    /**
     * Checks the flow class and creates a simulated virtual node for the given holding identity with a list of
     * [Flow] classes that the node can run. See [net.corda.simulator.tools.FlowChecker] for details of exceptions
     * that can be thrown.
     *
     * @param holdingIdentity the holding identity for which to create the flow
     * @param flowClasses a list of flow classes to be checked
     * @return a simulated virtual node in which flows can be run
     */
    fun createVirtualNode(
        holdingIdentity: HoldingIdentity,
        vararg flowClasses: Class<out Flow>
    ): SimulatedVirtualNode

    /**
     * Creates a simulated virtual node holding a concrete instance of a responder flow. Note that this bypasses all
     * checks for constructor and annotations on the flow.
     *
     * @param responder the holding identity which will respond with this flow
     * @param protocol the protocol for which this responder instance should be run
     * @param responderFlow an instance of a responder flow
     * @return a simulated virtual node which can run this instance of a responder flow
     */
    fun createVirtualNode(
        responder: HoldingIdentity,
        protocol: String,
        responderFlow: ResponderFlow
    ): SimulatedVirtualNode
}

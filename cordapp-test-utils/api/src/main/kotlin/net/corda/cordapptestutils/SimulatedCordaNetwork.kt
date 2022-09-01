package net.corda.cordapptestutils

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.annotations.DoNotImplement
import java.io.Closeable

@DoNotImplement
interface SimulatedCordaNetwork : Closeable {
    fun createVirtualNode(
        holdingIdentity: HoldingIdentity,
        vararg flowClasses: Class<out Flow>)
    : SimulatedVirtualNode

    /**
     * Creates a virtual node holding a concrete instance of a responder flow. Note that this bypasses all
     * checks for constructor and annotations on the flow.
     */
    fun createVirtualNode(
        responder: HoldingIdentity,
        protocol: String,
        responderFlow: ResponderFlow)
    : SimulatedVirtualNode
}

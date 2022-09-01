package net.corda.cordapptestutils

import net.corda.cordapptestutils.exceptions.ServiceConfigurationException
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.ResponderFlow
import java.util.ServiceLoader

class Simulator : SimulatedCordaNetwork {

    private val delegate : SimulatedCordaNetwork =
        ServiceLoader.load(SimulatedCordaNetwork::class.java).firstOrNull() ?:
        throw ServiceConfigurationException(SimulatedCordaNetwork::class.java)

    override fun createVirtualNode(
        holdingIdentity: HoldingIdentity,
        vararg flowClasses: Class<out Flow>
    ): SimulatedVirtualNode {
        return delegate.createVirtualNode(holdingIdentity, *flowClasses)
    }

    override fun createVirtualNode(
        responder: HoldingIdentity,
        protocol: String,
        responderFlow: ResponderFlow
    ): SimulatedVirtualNode {
        return delegate.createVirtualNode(responder, protocol, responderFlow)
    }

    override fun close() {
        return delegate.close()
    }
}

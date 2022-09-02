package net.corda.cordapptestutils

import net.corda.cordapptestutils.exceptions.ServiceConfigurationException
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.ResponderFlow
import java.util.ServiceLoader

/**
 * Simulator is a simulated Corda 5 network which will run in-process. It allows a lightweight "virtual node" to be
 * created which can run flows against a given member. Note that the flows in nodes do not have to be symmetrical;
 * an initiating flow class can be registered for one party with a responding flow class registered for another.
 *
 * Simulator uses lightweight versions of Corda services to help mimic the Corda network while ensuring that your
 * flow will work well with the real thing. These can be mocked out or wrapped if required, but most of the time the
 * defaults will be enough.
 *
 * Instances of initiator and responder flows can also be "uploaded"; however, these will not undergo the same checks
 * as a flow class "upload". This should generally only be used for mocked or faked flows to test matching responder or
 * initiator flows in isolation.
 *
 * @flowChecker Checks any flow class. Defaults to checking the various hooks which the real Corda would require
 * @fiber The simulated "fiber" with which responder flows will be registered by protocol
 * @injector An injector to initialize services annotated with @CordaInject in flows and subflows
 */
class Simulator : SimulatedCordaNetwork {

    private val delegate : SimulatedCordaNetwork =
        ServiceLoader.load(SimulatedCordaNetwork::class.java).firstOrNull() ?:
        throw ServiceConfigurationException(SimulatedCordaNetwork::class.java)

    /**
     * Registers a flow class against a given member with Simulator. Flow classes will be checked
     * for validity. Responder flows will also be registered against their protocols.
     *
     * @member The member for whom this node will be created
     * @flowClasses The flows which will be available to run in the nodes. Must be `RPCStartableFlow`
     * or `ResponderFlow`
     */
    override fun createVirtualNode(
        holdingIdentity: HoldingIdentity,
        vararg flowClasses: Class<out Flow>
    ): SimulatedVirtualNode {
        return delegate.createVirtualNode(holdingIdentity, *flowClasses)
    }

    /**
     * Creates a virtual node holding a concrete instance of a responder flow. Note that this bypasses all
     * checks for constructor and annotations on the flow.
     *
     * @responder The member for whom this node will be created
     * @protocol The protocol for which this flow will be called (does not need to be annotated on the flow)
     * @responderFlow The instance of flow to be called in response to an initiating flow with the given protocol
     */
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

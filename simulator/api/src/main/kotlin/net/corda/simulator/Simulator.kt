package net.corda.simulator

import net.corda.simulator.exceptions.ServiceConfigurationException
import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.factories.SimulatorDelegateFactory
import net.corda.v5.application.flows.Flow
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
class Simulator(
    configuration: SimulatorConfiguration = SimulatorConfigurationBuilder.create().build()
) : SimulatedCordaNetwork {

    private val delegate : SimulatedCordaNetwork =
        ServiceLoader.load(SimulatorDelegateFactory::class.java).firstOrNull()?.create(configuration) ?:
        throw ServiceConfigurationException(SimulatedCordaNetwork::class.java)

    /**
     * Checks the flow class and creates a simulated virtual node for the given holding identity with a list of
     * [Flow] classes that the node can run. See [net.corda.simulator.tools.FlowChecker] for details of exceptions
     * that can be thrown.
     *
     * @param holdingIdentity The holding identity for which to create the flow.
     * @param flowClasses A list of flow classes to be checked.
     * @return A simulated virtual node in which flows can be run.
     */
    @SafeVarargs
    override fun createVirtualNode(
        holdingIdentity: HoldingIdentity,
        vararg flowClasses: Class<out Flow>
    ): SimulatedVirtualNode {
        return delegate.createVirtualNode(holdingIdentity, *flowClasses)
    }

    /**
     * Creates a simulated virtual node holding a concrete instance of a responder flow. Note that this bypasses all
     * checks for constructor and annotations on the flow.
     *
     * @param holdingIdentity The holding identity which will call/respond to this flow.
     * @param protocol The protocol for which this responder instance should be run.
     * @param instanceFlow An instance of a responder/initiator flow.
     * @return A simulated virtual node which can run this instance of a responder flow.
     */
    override fun createInstanceNode(
        holdingIdentity: HoldingIdentity,
        protocol: String,
        flow: Flow
    ): SimulatedInstanceNode {
        return delegate.createInstanceNode(holdingIdentity, protocol, flow)
    }

    /**
     * Closes Simulator, releasing all resources including any database connections and in-memory databases.
     */
    override fun close() {
        return delegate.close()
    }
}

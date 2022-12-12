package net.corda.simulator.runtime

import net.corda.simulator.HoldingIdentity
import net.corda.simulator.SimulatedCordaNetwork
import net.corda.simulator.SimulatedVirtualNode
import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.runtime.flows.BaseFlowFactory
import net.corda.simulator.runtime.flows.DefaultServicesInjector
import net.corda.simulator.runtime.flows.FlowFactory
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.messaging.SimFiberBase
import net.corda.simulator.runtime.tools.CordaFlowChecker
import net.corda.simulator.tools.FlowChecker
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger


/**
 * The base class to which Simulator delegates.
 *
 * @param configuration The configuration for this instance of Simulator.
 * @param flowChecker A flow checker for checking flows.
 * @param fiber The simulated fiber / Kafka bus on which all shared state is stored.
 * @param injector The injector for services on flows.
 *
 * @see [net.corda.simulator.Simulator] for details.
 */
class SimulatorDelegateBase  (
    private val configuration: SimulatorConfiguration,
    private val flowChecker: FlowChecker = CordaFlowChecker(),
    private val fiber: SimFiber = SimFiberBase(),
    private val injector: FlowServicesInjector = DefaultServicesInjector(configuration)
) : SimulatedCordaNetwork {

    companion object {
        val log = contextLogger()
    }

    private val flowFactory: FlowFactory = BaseFlowFactory()

    override fun createVirtualNode(
        holdingIdentity: HoldingIdentity,
        vararg flowClasses: Class<out Flow>
    ): SimulatedVirtualNode {
        log.info("Creating virtual node for \"${holdingIdentity.member}\", flow classes: ${flowClasses.map{it.name}}")
        require(flowClasses.isNotEmpty()) { "No flow classes provided" }
        flowClasses.forEach {
            flowChecker.check(it)
            registerWithFiber(holdingIdentity.member, it)
        }
        return SimulatedVirtualNodeBase(holdingIdentity, fiber, injector, flowFactory)
    }

    private fun registerWithFiber(
        member: MemberX500Name,
        flowClass: Class<out Flow>
    ) {
        val protocolIfResponder = flowClass.getAnnotation(InitiatedBy::class.java)?.protocol
        if (protocolIfResponder == null) {
            fiber.registerMember(member)
        } else {
            val responderFlowClass = castInitiatedFlowToResponder(flowClass)
            fiber.registerMember(member)
            fiber.registerResponderClass(member, protocolIfResponder, responderFlowClass)
        }
    }

    private fun castInitiatedFlowToResponder(flowClass: Class<out Flow>) : Class<ResponderFlow> {
        if (ResponderFlow::class.java.isAssignableFrom(flowClass)) {
            @Suppress("UNCHECKED_CAST")
            return flowClass as Class<ResponderFlow>
        } else throw IllegalArgumentException(
            "${flowClass.simpleName} has an @${InitiatedBy::class.java} annotation, but " +
                    "it is not a ${ResponderFlow::class.java}"
        )
    }

    override fun createVirtualNode(
        holdingIdentity: HoldingIdentity,
        protocol: String,
        instanceFlow: Flow
    ): SimulatedVirtualNode {
        log.info("Creating virtual node for \"${holdingIdentity.member}\", flow instance provided for protocol $protocol")
        fiber.registerMember(holdingIdentity.member)
        fiber.registerFlowInstance(holdingIdentity.member, protocol, instanceFlow)
        return SimulatedVirtualNodeBase(holdingIdentity, fiber, injector, flowFactory)
    }


    override fun close() {
        log.info("Closing Simulator")
        fiber.close()
    }
}
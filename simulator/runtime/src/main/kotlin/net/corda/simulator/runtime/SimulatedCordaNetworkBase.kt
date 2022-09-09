package net.corda.simulator.runtime

import net.corda.simulator.HoldingIdentity
import net.corda.simulator.SimulatedCordaNetwork
import net.corda.simulator.SimulatedVirtualNode
import net.corda.simulator.runtime.flows.BaseFlowFactory
import net.corda.simulator.runtime.flows.DefaultServicesInjector
import net.corda.simulator.runtime.flows.FlowFactory
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.messaging.SimFiberBase
import net.corda.simulator.runtime.signing.BaseSimKeyStore
import net.corda.simulator.runtime.tools.CordaFlowChecker
import net.corda.simulator.tools.FlowChecker
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name


class SimulatedCordaNetworkBase  (
    private val flowChecker: FlowChecker = CordaFlowChecker(),
    private val fiber: SimFiber = SimFiberBase(),
    private val injector: FlowServicesInjector = DefaultServicesInjector()
) : SimulatedCordaNetwork {

    private val flowFactory: FlowFactory = BaseFlowFactory()

    override fun createVirtualNode(
        holdingIdentity: HoldingIdentity,
        vararg flowClasses: Class<out Flow>
    ): SimulatedVirtualNode {
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
            fiber.registerInitiator(member)
        } else {
            val responderFlowClass = castInitiatedFlowToResponder(flowClass)
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
        responder: HoldingIdentity,
        protocol: String,
        responderFlow: ResponderFlow
    ): SimulatedVirtualNode {
        fiber.registerResponderInstance(responder.member, protocol, responderFlow)
        return SimulatedVirtualNodeBase(responder, fiber, injector, flowFactory, BaseSimKeyStore())
    }

    override fun close() {
        fiber.close()
    }
}
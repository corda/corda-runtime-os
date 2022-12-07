package net.corda.simulator.runtime

import net.corda.simulator.HoldingIdentity
import net.corda.simulator.RequestData
import net.corda.simulator.SimulatedVirtualNode
import net.corda.simulator.runtime.flows.BaseFlowManager
import net.corda.simulator.runtime.flows.FlowFactory
import net.corda.simulator.runtime.flows.FlowManager
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.messaging.SimFlowContextProperties
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger

/**
 * The base class for simulated virtual nodes.
 *
 * @property holdingIdentity The holding identity of the node.
 * @param fiber The simulated fiber / kafka bus on which shared state is stored.
 * @param injector The injector to initialize services on flows called in this node.
 * @param flowFactory A flow factory for creating called flows.
 */
class SimulatedVirtualNodeBase(
    override val holdingIdentity: HoldingIdentity,
    override val fiber: SimFiber,
    private val injector: FlowServicesInjector,
    private val flowFactory: FlowFactory,
    private val flowManager: FlowManager = BaseFlowManager()
) : SimulatedNodeBase(), SimulatedVirtualNode {

    companion object {
        val log = contextLogger()
    }

    override val member : MemberX500Name = holdingIdentity.member
    override fun callFlow(input: RequestData): String {
        return doCallFlow(input, emptyMap())
    }

    override fun callFlow(input: RequestData, contextPropertiesMap: Map<String, String>): String {
        return doCallFlow(input, contextPropertiesMap)
    }

    private fun doCallFlow(input: RequestData, contextPropertiesMap: Map<String, String>): String{
        val flowClassName = input.flowClassName
        log.info("Calling flow $flowClassName for member \"$member\" with request: ${input.requestBody}")
        val flow = flowFactory.createInitiatingFlow(member, flowClassName)
        injector.injectServices(flow, member, fiber, SimFlowContextProperties(contextPropertiesMap))
        val result = flowManager.call(input.toRPCRequestData(), flow)
        log.info("Finished flow $flowClassName for member \"$member\"")
        return result
    }
}

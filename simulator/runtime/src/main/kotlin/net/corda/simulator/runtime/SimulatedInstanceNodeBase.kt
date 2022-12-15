package net.corda.simulator.runtime

import net.corda.simulator.HoldingIdentity
import net.corda.simulator.RequestData
import net.corda.simulator.SimulatedInstanceNode
import net.corda.simulator.runtime.flows.BaseFlowManager
import net.corda.simulator.runtime.flows.FlowFactory
import net.corda.simulator.runtime.flows.FlowManager
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.messaging.SimFlowContextProperties
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger

/**
 * See [SimulatedInstanceNode].
 */
@Suppress("LongParameterList")
class SimulatedInstanceNodeBase(
    override val holdingIdentity: HoldingIdentity,
    private val protocol: String,
    private val flow: Flow,
    override val fiber: SimFiber,
    private val injector: FlowServicesInjector,
    private val flowFactory: FlowFactory,
    private val flowManager: FlowManager = BaseFlowManager()
) : SimulatedNodeBase(), SimulatedInstanceNode {

    override val member : MemberX500Name = holdingIdentity.member

    init {
        fiber.registerMember(member)
        if (ResponderFlow::class.java.isInstance(flow)) {
            fiber.registerFlowInstance(member, protocol, flow)
        } else if (!RPCStartableFlow::class.java.isInstance(flow)) {
            error("The flow provided to this node was neither a `ResponderFlow` nor an `RPCStartableFlow`.")
        }
    }

    companion object {
        val log = contextLogger()
    }

    override fun callInstanceFlow(input: RequestData): String {
        return doCallFlow(input, emptyMap())
    }

    override fun callInstanceFlow(input: RequestData, contextPropertiesMap: Map<String, String>): String {
        return doCallFlow(input, contextPropertiesMap)
    }

    private fun doCallFlow(input: RequestData, contextPropertiesMap: Map<String, String>): String{
        if (flow !is RPCStartableFlow) {
            error("The flow provided to the instance node for member \"$member\" and protocol $protocol " +
                    "was not an RPCStartableFlow")
        }
        log.info("Calling flow instance for member \"$member\" and protocol \"$protocol\" " +
                "with request: ${input.requestBody}")
        injector.injectServices(flow, member, fiber, SimFlowContextProperties(contextPropertiesMap))
        val result = flowManager.call(input.toRPCRequestData(), flow)
        SimulatedVirtualNodeBase.log.info("Finished flow instance for member \"$member\" and protocol \"$protocol\"")
        return result
    }

}

package net.corda.simulator.runtime

import net.corda.simulator.HoldingIdentity
import net.corda.simulator.RequestData
import net.corda.simulator.SimulatedInstanceNode
import net.corda.simulator.runtime.flows.BaseFlowManager
import net.corda.simulator.runtime.flows.FlowAndProtocol
import net.corda.simulator.runtime.flows.FlowManager
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.messaging.SimFlowContextProperties
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RestStartableFlow
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
    private val flowManager: FlowManager = BaseFlowManager()
) : SimulatedNodeBase(), SimulatedInstanceNode {

    override val member : MemberX500Name = holdingIdentity.member

    init {
        fiber.registerMember(member)
        if (ResponderFlow::class.java.isInstance(flow)) {
            fiber.registerResponderInstance(member, protocol, flow as ResponderFlow)
        } else if (!RestStartableFlow::class.java.isInstance(flow)){
            error("The flow provided to this node was neither a `ResponderFlow` nor an `RestStartableFlow`.")
        }
    }

    companion object {
        val log = contextLogger()
    }

    override fun callInstanceFlow(input: RequestData): String {
        return callInstanceFlow(input, emptyMap())
    }

    override fun callInstanceFlow(input: RequestData, contextPropertiesMap: Map<String, String>): String {
        if (flow !is RestStartableFlow) {
            error(
                "The flow provided to the instance node for member \"$member\" and protocol $protocol " +
                        "was not an RestStartableFlow"
            )
        }
        log.info(
            "Calling flow instance for member \"$member\" and protocol \"$protocol\" " +
                    "with request: ${input.requestBody}"
        )
        injector.injectServices(
            FlowAndProtocol(flow, protocol),
            member,
            fiber,
            SimFlowContextProperties(contextPropertiesMap)
        )
        val result = flowManager.call(input.toRPCRequestData(), flow)
        SimulatedVirtualNodeBase.log.info("Finished flow instance for member \"$member\" and protocol \"$protocol\"")
        return result
    }

}

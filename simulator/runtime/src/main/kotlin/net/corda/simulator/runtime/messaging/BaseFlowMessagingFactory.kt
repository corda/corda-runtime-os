package net.corda.simulator.runtime.messaging

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.runtime.flows.BaseFlowFactory
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.runtime.utils.getProtocol
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.types.MemberX500Name

class BaseFlowMessagingFactory: FlowMessagingFactory {

    override fun createFlowMessaging(
        configuration: SimulatorConfiguration,
        member: MemberX500Name,
        fiber: SimFiber,
        injector: FlowServicesInjector,
        flow: Flow
    ): FlowMessaging {

        val instanceFlowMap = fiber.lookupFlowInstance(member)

        val protocol: String = if(instanceFlowMap ==null || instanceFlowMap[flow] == null) {
            flow.getProtocol()
        }else{
            instanceFlowMap[flow]!!
        }

        return ConcurrentFlowMessaging(
            FlowContext(configuration, member, protocol),
            fiber,
            injector,
            BaseFlowFactory()
        )
    }
}
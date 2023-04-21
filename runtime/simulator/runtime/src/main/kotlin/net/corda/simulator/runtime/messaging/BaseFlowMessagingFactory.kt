package net.corda.simulator.runtime.messaging

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.exceptions.NoProtocolAnnotationException
import net.corda.simulator.runtime.flows.BaseFlowFactory
import net.corda.simulator.runtime.flows.FlowAndProtocol
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.types.MemberX500Name

class BaseFlowMessagingFactory: FlowMessagingFactory {

    override fun createFlowMessaging(
        configuration: SimulatorConfiguration,
        member: MemberX500Name,
        fiber: SimFiber,
        injector: FlowServicesInjector,
        flow: FlowAndProtocol,
        contextProperties: FlowContextProperties
    ): FlowMessaging {
        if(flow.protocol == null) throw NoProtocolAnnotationException(flow::class.java)

        return ConcurrentFlowMessaging(
            FlowContext(configuration, member, flow.protocol),
            fiber,
            injector,
            BaseFlowFactory(),
            contextProperties
        )
    }
}
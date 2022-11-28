package net.corda.simulator.runtime.messaging

import net.corda.simulator.runtime.flows.BaseFlowFactory
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.v5.application.messaging.FlowMessaging

class BaseFlowMessagingFactory: FlowMessagingFactory {

    override fun createFlowMessaging(
        flowDetails: FlowContext,
        fiber: SimFiber,
        injector: FlowServicesInjector
    ): FlowMessaging {
        return ConcurrentFlowMessaging(
            flowDetails,
            fiber,
            injector,
            BaseFlowFactory()
        )
    }
}
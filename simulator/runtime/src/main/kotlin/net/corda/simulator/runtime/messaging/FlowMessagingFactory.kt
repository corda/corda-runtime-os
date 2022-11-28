package net.corda.simulator.runtime.messaging

import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.v5.application.messaging.FlowMessaging

interface FlowMessagingFactory {

    fun createFlowMessaging(flowDetails: FlowContext,
                            fiber: SimFiber,
                            injector: FlowServicesInjector): FlowMessaging
}
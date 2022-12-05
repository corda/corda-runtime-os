package net.corda.simulator.runtime.messaging

import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.v5.application.messaging.FlowMessaging

/**
 * Creates [FlowMessaging] for simulated flows
 */
interface FlowMessagingFactory {

    /**
     * @param flowDetails The [FlowContext] for the flow
     * @param fiber The [SimFiber] of simulator
     * @param injector to inject flow services
     */
    fun createFlowMessaging(flowDetails: FlowContext,
                            fiber: SimFiber,
                            injector: FlowServicesInjector): FlowMessaging
}
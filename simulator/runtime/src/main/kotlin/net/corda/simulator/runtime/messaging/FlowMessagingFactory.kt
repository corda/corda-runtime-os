package net.corda.simulator.runtime.messaging

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.types.MemberX500Name

/**
 * Creates [FlowMessaging] for simulated flows
 */
interface FlowMessagingFactory {

    /**
     * @param flowDetails The [FlowContext] for the flow
     * @param fiber The [SimFiber] of simulator
     * @param injector to inject flow services
     */
    fun createFlowMessaging(configuration: SimulatorConfiguration,
                            member: MemberX500Name,
                            fiber: SimFiber,
                            injector: FlowServicesInjector,
                            flow: Flow): FlowMessaging
}
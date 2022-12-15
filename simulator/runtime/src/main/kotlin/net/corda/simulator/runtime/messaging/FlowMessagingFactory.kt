package net.corda.simulator.runtime.messaging

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.types.MemberX500Name

/**
 * Creates [FlowMessaging] for simulated flows
 */
interface FlowMessagingFactory {

    /**
     * @param configuration The configuration for the simulator.
     * @param member The name of the "virtual node".
     * @param fiber The [SimFiber] of simulator.
     * @param injector to inject flow services.
     * @param flow for which the messaging service is to be created.
     * @param contextProperties The [FlowContextProperties] for the flow.
     */
    @Suppress("LongParameterList")
    fun createFlowMessaging(configuration: SimulatorConfiguration,
                            member: MemberX500Name,
                            fiber: SimFiber,
                            injector: FlowServicesInjector,
                            flow: Flow,
                            contextProperties: FlowContextProperties): FlowMessaging
}
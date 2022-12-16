package net.corda.simulator.runtime.flows

import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.base.types.MemberX500Name


interface FlowServicesInjector {
    /**
     * Injects services into the provided flow.
     *
     * @param flow The flow to inject services into.
     * @param member The name of the "virtual node".
     * @param fiber The "fiber" through which flow messaging will look up peers.
     * @param contextProperties The [FlowContextProperties] for the flow.
     */
    fun injectServices(
        flow: Flow,
        member: MemberX500Name,
        fiber: SimFiber,
        contextProperties: FlowContextProperties
    )
}
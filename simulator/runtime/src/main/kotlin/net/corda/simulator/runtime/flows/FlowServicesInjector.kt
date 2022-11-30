package net.corda.simulator.runtime.flows

import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.types.MemberX500Name


interface FlowServicesInjector {
    /**
     * Injects services into the provided flow.
     *
     * @param flow The flow to inject services into.
     * @param member The name of the "virtual node".
     * @param protocolLookUp The "fiber" through which flow messaging will look up peers.
     * @param flowFactory A factory for constructing flows (defaults to a simple base implementation).
     * @param keyStore The key store for the given member.
     */
    fun injectServices(
        flow: Flow,
        member: MemberX500Name,
        fiber: SimFiber,
        flowFactory: FlowFactory = BaseFlowFactory()
    )
}
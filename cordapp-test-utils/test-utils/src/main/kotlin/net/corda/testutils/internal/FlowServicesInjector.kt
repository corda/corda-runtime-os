package net.corda.testutils.internal

import net.corda.v5.application.flows.Flow
import net.corda.v5.base.types.MemberX500Name


interface FlowServicesInjector {
    /**
     * Injects services into the provided flow.
     *
     * @flow The flow to inject services into
     * @member The name of the "virtual node"
     * @protocolLookUp The "fiber" through which flow messaging will look up peers
     * @flowFactory A factory for constructing flows (defaults to a simple base implementation)
     */
    fun injectServices(
        flow: Flow,
        member: MemberX500Name,
        fakeFiber: FakeFiber,
        flowFactory: FlowFactory = BaseFlowFactory()
    )
}
package net.corda.v5.application.services.flows

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.application.messaging.FlowHandle
import net.corda.v5.base.annotations.Suspendable

interface FlowStarterService : CordaServiceInjectable {
    
    /**
     * Start the given flow with the given arguments. [flow] must be annotated
     * with [net.corda.v5.application.flows.StartableByService].
     * 
     * @param flow
     */
    @Suspendable
    fun <T> startFlow(flow: Flow<T>): FlowHandle<T>
}
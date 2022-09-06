package net.cordacon.example

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable

@InitiatedBy("absence-call")
class AbsenceCallResponderFlow: ResponderFlow {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        ResponderFlowDelegate().callDelegate(session, flowEngine)
    }
}
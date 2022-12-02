package net.cordacon.example.rollcall

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

@InitiatedBy("absence-call")
class AbsenceCallResponderFlow: ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("${flowEngine.virtualNodeName} received request; sending response")

        ResponderFlowDelegate().callDelegate(session, flowEngine)

        log.info("${flowEngine.virtualNodeName} sent response")
    }
}